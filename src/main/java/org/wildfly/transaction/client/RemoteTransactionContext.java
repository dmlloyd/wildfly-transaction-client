/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.transaction.client;

import static java.security.AccessController.doPrivileged;

import java.net.URI;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.UserTransaction;

import org.wildfly.common.Assert;
import org.wildfly.common.context.ContextManager;
import org.wildfly.common.context.Contextual;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.transaction.TransactionPermission;
import org.wildfly.transaction.client._private.Log;
import org.wildfly.transaction.client.spi.RemoteTransactionProvider;

/**
 * The remote transaction context.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class RemoteTransactionContext implements Contextual<RemoteTransactionContext> {

    private static final RemoteTransactionProvider[] NO_PROVIDERS = new RemoteTransactionProvider[0];
    private static final TransactionPermission CREATION_LISTENER_PERMISSION = TransactionPermission.forName("registerCreationListener");

    private final List<RemoteTransactionProvider> providers;
    private final List<CreationListener> creationListeners = new CopyOnWriteArrayList<>();

    /**
     * Construct a new instance.  The given class loader is scanned for transaction providers.
     *
     * @param classLoader the class loader to scan for transaction providers ({@code null} indicates the application or bootstrap class loader)
     */
    public RemoteTransactionContext(final ClassLoader classLoader) {
        this(doPrivileged((PrivilegedAction<List<RemoteTransactionProvider>>) () -> {
            final ServiceLoader<RemoteTransactionProvider> loader = ServiceLoader.load(RemoteTransactionProvider.class, classLoader);
            final ArrayList<RemoteTransactionProvider> providers = new ArrayList<RemoteTransactionProvider>();
            final Iterator<RemoteTransactionProvider> iterator = loader.iterator();
            for (;;) try {
                if (! iterator.hasNext()) break;
                providers.add(iterator.next());
            } catch (ServiceConfigurationError e) {
                Log.log.serviceConfigurationFailed(e);
            }
            providers.trimToSize();
            return providers;
        }), false);
    }

    /**
     * Construct a new instance.  The given non-empty list of providers is used.
     *
     * @param providers the list of providers to use (must not be {@code null} or empty)
     */
    public RemoteTransactionContext(final List<RemoteTransactionProvider> providers) {
        this(providers, true);
    }

    RemoteTransaction notifyCreationListeners(RemoteTransaction transaction, CreationListener.CreatedBy createdBy) {
        for (CreationListener creationListener : creationListeners) {
            try {
                creationListener.transactionCreated(transaction, createdBy);
            } catch (Throwable t) {
                Log.log.trace("Transaction creation listener throws an exception", t);
            }
        }
        return transaction;
    }

    /**
     * Register a transaction creation listener.
     *
     * @param creationListener the creation listener (must not be {@code null})
     */
    public void registerCreationListener(CreationListener creationListener) {
        Assert.checkNotNullParam("creationListener", creationListener);
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(CREATION_LISTENER_PERMISSION);
        }
        creationListeners.add(creationListener);
    }

    /**
     * Remove a transaction creation listener.
     *
     * @param creationListener the creation listener (must not be {@code null})
     */
    public void removeCreationListener(CreationListener creationListener) {
        Assert.checkNotNullParam("creationListener", creationListener);
        creationListeners.removeIf(c -> c == creationListener);
    }

    RemoteTransactionContext(List<RemoteTransactionProvider> providers, boolean clone) {
        Assert.checkNotNullParam("providers", providers);
        if (clone) {
            providers = Arrays.asList(providers.toArray(NO_PROVIDERS));
        }
        Assert.checkNotEmptyParam("providers", providers);
        final int size = providers.size();
        for (int i = 0; i < size; i++) {
            Assert.checkNotNullArrayParam("providers", i, providers.get(i));
        }
        this.providers = providers;
    }

    private static final ContextManager<RemoteTransactionContext> CONTEXT_MANAGER = new ContextManager<RemoteTransactionContext>(RemoteTransactionContext.class, "org.wildfly.transaction.client.context.remote");
    private static final Supplier<RemoteTransactionContext> PRIVILEGED_SUPPLIER = doPrivileged((PrivilegedAction<Supplier<RemoteTransactionContext>>) CONTEXT_MANAGER::getPrivilegedSupplier);

    static {
        doPrivileged((PrivilegedAction<Void>) () -> {
            CONTEXT_MANAGER.setGlobalDefault(new RemoteTransactionContext(RemoteTransactionContext.class.getClassLoader()));
            return null;
        });
    }

    /**
     * Get the remote transaction context manager.
     *
     * @return the context manager
     */
    public static ContextManager<RemoteTransactionContext> getContextManager() {
        return CONTEXT_MANAGER;
    }

    /** {@inheritDoc} */
    public ContextManager<RemoteTransactionContext> getInstanceContextManager() {
        return getContextManager();
    }

    /**
     * Get the active remote transaction context instance.
     *
     * @return the remote transaction context instance (not {@code null})
     */
    public static RemoteTransactionContext getInstance() {
        return getContextManager().get();
    }

    static RemoteTransactionContext getInstancePrivate() {
        return PRIVILEGED_SUPPLIER.get();
    }

    /**
     * Get a {@code UserTransaction} that controls a remote transactions state at the given {@code location}.  The transaction
     * context may cache these instances by location.
     *
     * @return the {@code UserTransaction} (not {@code null})
     */
    public RemoteUserTransaction getUserTransaction() {
        return new RemoteUserTransaction(AuthenticationContext.captureCurrent());
    }

    /**
     * Compatibility bridge method.
     *
     * @return the {@code UserTransaction} (not {@code null})
     * @deprecated Please use {@link #getUserTransaction()} instead.
     */
    public UserTransaction getUserTransaction$$bridge_compat() {
        return getUserTransaction();
    }

    private static final Object outflowKey = new Object();

    /**
     * Outflow the given local transaction to the given location.  The returned handle
     * must be used to confirm or forget the enlistment attempt either immediately or at some point in the future;
     * failure to do so may cause the transaction to be rolled back with an error.
     *
     * @param location the location to outflow to (must not be {@code null})
     * @param transaction the transaction (must not be {@code null})
     * @return the enlistment handle (not {@code null})
     * @throws SystemException if the transaction manager fails for some reason
     * @throws RollbackException if the transaction has been rolled back in the meantime
     * @throws IllegalStateException if no transaction is active
     * @throws UnsupportedOperationException if the provider for the location does not support outflow
     */
    public XAOutflowHandle outflowTransaction(final URI location, final LocalTransaction transaction) throws SystemException, IllegalStateException, UnsupportedOperationException, RollbackException {
        Assert.checkNotNullParam("location", location);
        Assert.checkNotNullParam("transaction", transaction);

        XAOutflowedResources outflowedResources = getOutflowedResources(transaction);
        if (outflowedResources == null) {
            synchronized (transaction.getOutflowLock()) {
                outflowedResources = getOutflowedResources(transaction);
                if (outflowedResources == null) {
                    transaction.putResource(outflowKey, outflowedResources = new XAOutflowedResources(transaction));
                }
            }
        }
        SubordinateXAResource resource = outflowedResources.getOrEnlist(location, transaction.getParentName());
        return resource.addHandle(resource.getXid());
    }

    static XAOutflowedResources getOutflowedResources(final LocalTransaction transaction) {
        return (XAOutflowedResources) transaction.getResource(outflowKey);
    }

    RemoteTransactionProvider getProvider(final URI location) {
        for (RemoteTransactionProvider provider : providers) {
            if (provider.supportsScheme(location.getScheme())) {
                return provider;
            }
        }
        return null;
    }
}
