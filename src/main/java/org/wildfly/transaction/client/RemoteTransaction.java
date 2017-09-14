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

import java.net.URI;
import java.security.AccessController;
import java.security.GeneralSecurityException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLContext;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.Synchronization;
import javax.transaction.SystemException;
import javax.transaction.xa.XAResource;

import org.wildfly.common.Assert;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.client.AuthenticationContextConfigurationClient;
import org.wildfly.transaction.TransactionPermission;
import org.wildfly.transaction.client._private.Log;
import org.wildfly.transaction.client.spi.RemoteTransactionProvider;
import org.wildfly.transaction.client.spi.SimpleTransactionControl;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class RemoteTransaction extends AbstractTransaction {
    private final AtomicReference<State> stateRef;
    private final ConcurrentMap<Object, Object> resources = new ConcurrentHashMap<>();
    private final AuthenticationContext authenticationContext;
    private final Object key = new Object();
    private final int timeout;

    static final AuthenticationContextConfigurationClient CLIENT = AccessController.doPrivileged(AuthenticationContextConfigurationClient.ACTION);

    RemoteTransaction(final AuthenticationContext authenticationContext, final int timeout) {
        this.authenticationContext = authenticationContext;
        stateRef = new AtomicReference<>(Unlocated.ACTIVE);
        this.timeout = timeout;
    }

    Object getResource(final Object key) throws NullPointerException {
        Assert.checkNotNullParamWithNullPointerException("key", key);
        return resources.get(key);
    }

    void putResource(final Object key, final Object value) throws NullPointerException {
        Assert.checkNotNullParamWithNullPointerException("key", key);
        if (value == null) resources.remove(key); else resources.put(key, value);
    }

    Object getKey() {
        return key;
    }

    void suspend() {
        notifyAssociationListeners(false);
        // no operation
    }

    void resume() {
        notifyAssociationListeners(true);
        // no operation
    }

    void verifyAssociation() {
        // no operation
    }

    public int getTransactionTimeout() {
        return timeout;
    }

    public <T> T getProviderInterface(final Class<T> providerInterfaceType) {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(TransactionPermission.forName("getProviderInterface"));
        }
        return stateRef.get().getProviderInterface(providerInterfaceType);
    }

    public void commit() throws RollbackException, HeuristicMixedException, HeuristicRollbackException, SecurityException, SystemException {
        final AtomicReference<State> stateRef = this.stateRef;
        stateRef.get().commit(stateRef);
    }

    void commitAndDissociate() throws RollbackException, HeuristicMixedException, HeuristicRollbackException, SecurityException, SystemException {
        try {
            commit();
        } finally {
            suspend();
        }
    }

    public void rollback() throws IllegalStateException, SystemException {
        final AtomicReference<State> stateRef = this.stateRef;
        stateRef.get().rollback(stateRef);
    }

    void rollbackAndDissociate() throws IllegalStateException, SystemException {
        try {
            rollback();
        } finally {
            suspend();
        }
    }

    boolean importBacking() {
        return false;
    }

    void unimportBacking() {
    }

    public void setRollbackOnly() throws IllegalStateException, SystemException {
        final AtomicReference<State> stateRef = this.stateRef;
        stateRef.get().setRollbackOnly(stateRef);
    }

    public int getStatus() {
        return stateRef.get().getStatus();
    }

    public boolean enlistResource(final XAResource xaRes) {
        Assert.checkNotNullParam("xaRes", xaRes);
        return false;
    }

    public boolean delistResource(final XAResource xaRes, final int flag) {
        Assert.checkNotNullParam("xaRes", xaRes);
        return false;
    }

    public void registerSynchronization(final Synchronization sync) throws RollbackException, IllegalStateException, SystemException {
        Assert.checkNotNullParam("sync", sync);
        stateRef.get().registerSynchronization(new AssociatingSynchronization(sync));
    }

    void registerInterposedSynchronization(final Synchronization sync) throws IllegalStateException {
        Assert.checkNotNullParam("sync", sync);
        stateRef.get().registerInterposedSynchronization(new AssociatingSynchronization(sync));
    }

    public int hashCode() {
        return System.identityHashCode(this);
    }

    public boolean equals(final Object obj) {
        return obj == this;
    }

    public String toString() {
        return String.format("Remote transaction %s", stateRef.get());
    }

    /**
     * Get the location of this remote transaction.
     *
     * @return the location of this remote transaction, or {@code null} if it has no location as of yet
     */
    public URI getLocation() {
        return stateRef.get().getLocation();
    }

    /**
     * Attempt to set the location of this transaction, binding it to a remote transport provider.
     *
     * @param location the location to set (must not be {@code null})
     * @throws IllegalArgumentException if there is no provider for the given location (or it is {@code null}), or the
     *  security context was invalid
     * @throws IllegalStateException if the transaction is in an invalid state for setting its location
     * @throws SystemException if the transport could not begin the transaction
     */
    public void setLocation(URI location) throws IllegalArgumentException, IllegalStateException, SystemException {
        Assert.checkNotNullParam("location", location);
        final RemoteTransactionContext context = RemoteTransactionContext.getInstancePrivate();
        final RemoteTransactionProvider provider = context.getProvider(location);
        if (provider == null) {
            throw Log.log.noProviderForUri(location);
        }
        final AuthenticationContextConfigurationClient client = CLIENT;
        final AuthenticationConfiguration authenticationConfiguration = client.getAuthenticationConfiguration(location, authenticationContext, - 1, "jta", "jboss");
        final SSLContext sslContext;
        try {
            sslContext = client.getSSLContext(location, authenticationContext, "jta", "jboss");
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException(e);
        }
        final SimpleTransactionControl control = provider.getPeerHandle(location, sslContext, authenticationConfiguration).begin(getEstimatedRemainingTime());
        try {
            stateRef.get().join(stateRef, location, control);
        } catch (Throwable t) {
            try {
                control.rollback();
            } catch (Throwable t2) {
                t2.addSuppressed(t);
                throw t2;
            }
            throw t;
        }
    }

    abstract static class State {
        State() {
        }

        abstract void join(AtomicReference<State> stateRef, URI location, SimpleTransactionControl control) throws IllegalStateException;

        abstract void commit(AtomicReference<State> stateRef) throws RollbackException, HeuristicMixedException, HeuristicRollbackException, SecurityException, SystemException;

        abstract void rollback(AtomicReference<State> stateRef) throws IllegalStateException, SystemException;

        abstract void setRollbackOnly(AtomicReference<State> stateRef) throws IllegalStateException, SystemException;

        abstract int getStatus();

        abstract void registerSynchronization(Synchronization sync) throws RollbackException, IllegalStateException, SystemException;

        abstract void registerInterposedSynchronization(Synchronization sync) throws IllegalStateException;

        <T> T getProviderInterface(final Class<T> providerInterfaceType) {
            return null;
        }

        URI getLocation() {
            return null;
        }
    }

    abstract static class Unresolved extends State {

        Unresolved() {
        }

        void registerSynchronization(final Synchronization sync) throws RollbackException, IllegalStateException, SystemException {
            throw Log.log.registerSynchRemoteTransaction();
        }

        void registerInterposedSynchronization(final Synchronization sync) throws IllegalStateException {
            throw Log.log.registerSynchRemoteTransaction();
        }
    }

    static final class Unlocated extends Unresolved {
        private final int status;

        static final Unlocated ACTIVE = new Unlocated(Status.STATUS_ACTIVE);
        static final Unlocated ROLLBACK_ONLY = new Unlocated(Status.STATUS_MARKED_ROLLBACK);

        Unlocated(final int status) {
            this.status = status;
        }

        void join(final AtomicReference<State> stateRef, final URI location, final SimpleTransactionControl control) throws IllegalStateException {
            State newState;
            switch (status) {
                case Status.STATUS_ACTIVE: newState = new Active(location, control); break;
                case Status.STATUS_MARKED_ROLLBACK: newState = new RollbackOnly(location, control); break;
                default: throw Assert.impossibleSwitchCase(status);
            }
            if (stateRef.compareAndSet(this, newState)) {
                return;
            } else {
                stateRef.get().join(stateRef, location, control);
            }
        }

        void commit(final AtomicReference<State> stateRef) {
            stateRef.set(InactiveState.COMMITTED);
        }

        void rollback(final AtomicReference<State> stateRef) throws IllegalStateException, SystemException {
            stateRef.set(InactiveState.ROLLED_BACK);
        }

        void setRollbackOnly(final AtomicReference<State> stateRef) throws IllegalStateException, SystemException {
            stateRef.set(Unlocated.ROLLBACK_ONLY);
        }

        int getStatus() {
            return status;
        }
    }

    abstract static class Located extends Unresolved {
        final URI location;
        final SimpleTransactionControl control;

        Located(final URI location, final SimpleTransactionControl control) {
            this.location = location;
            this.control = control;
        }

        void join(final AtomicReference<State> stateRef, final URI location, final SimpleTransactionControl control) throws IllegalStateException {
            if (! this.location.equals(location)) {
                throw Log.log.locationAlreadyInitialized(location, this.location);
            }
        }

        void rollback(final AtomicReference<State> stateRef) throws IllegalStateException, SystemException {
            if (! stateRef.compareAndSet(this, InactiveState.ROLLING_BACK)) {
                stateRef.get().rollback(stateRef);
                return;
            }
            try {
                control.rollback();
            } catch (SecurityException e) {
                stateRef.set(this);
                throw e;
            } catch (Throwable t) {
                stateRef.set(InactiveState.UNKNOWN);
                throw t;
            }
            stateRef.set(InactiveState.ROLLED_BACK);
        }

        <T> T getProviderInterface(final Class<T> providerInterfaceType) {
            return control.getProviderInterface(providerInterfaceType);
        }

        URI getLocation() {
            return location;
        }
    }

    static final class Active extends Located {

        Active(final URI location, final SimpleTransactionControl control) {
            super(location, control);
        }

        void commit(final AtomicReference<State> stateRef) throws RollbackException, HeuristicMixedException, HeuristicRollbackException, SecurityException, SystemException {
            if (! stateRef.compareAndSet(this, InactiveState.COMMITTING)) {
                stateRef.get().commit(stateRef);
                return;
            }
            try {
                control.commit();
            } catch (SecurityException e) {
                stateRef.set(this);
                throw e;
            } catch (RollbackException | HeuristicRollbackException e) {
                stateRef.set(InactiveState.ROLLED_BACK);
                throw e;
            } catch (Throwable t) {
                stateRef.set(InactiveState.UNKNOWN);
                throw t;
            }
            stateRef.set(InactiveState.COMMITTED);
        }

        int getStatus() {
            return Status.STATUS_ACTIVE;
        }

        void setRollbackOnly(final AtomicReference<State> stateRef) throws IllegalStateException, SystemException {
            synchronized (stateRef) {
                // there's no "marking rollback" state
                final RollbackOnly newState = new RollbackOnly(location, control);
                if (! stateRef.compareAndSet(this, newState)) {
                    stateRef.get().setRollbackOnly(stateRef);
                }
                control.setRollbackOnly();
            }
        }
    }

    static final class RollbackOnly extends Located {
        RollbackOnly(final URI location, final SimpleTransactionControl control) {
            super(location, control);
        }

        void commit(final AtomicReference<State> stateRef) throws RollbackException, HeuristicMixedException, HeuristicRollbackException, SecurityException, SystemException {
            rollback(stateRef);
            throw Log.log.rollbackOnlyRollback();
        }

        void setRollbackOnly(final AtomicReference<State> stateRef) {
            // no operation
        }

        int getStatus() {
            return Status.STATUS_MARKED_ROLLBACK;
        }

        void registerSynchronization(final Synchronization sync) throws RollbackException, IllegalStateException, SystemException {
            throw Log.log.markedRollbackOnly();
        }
    }

    static final class InactiveState extends State {
        private final int status;

        static final InactiveState ROLLED_BACK = new InactiveState(Status.STATUS_ROLLEDBACK);
        static final InactiveState COMMITTED = new InactiveState(Status.STATUS_COMMITTED);
        static final InactiveState UNKNOWN = new InactiveState(Status.STATUS_UNKNOWN);

        static final InactiveState COMMITTING = new InactiveState(Status.STATUS_COMMITTING);
        static final InactiveState ROLLING_BACK = new InactiveState(Status.STATUS_ROLLING_BACK);

        private InactiveState(final int status) {
            this.status = status;
        }

        void join(final AtomicReference<State> stateRef, final URI location, final SimpleTransactionControl control) throws IllegalStateException {
            throw Log.log.notActive();
        }

        void commit(final AtomicReference<State> stateRef) throws RollbackException, HeuristicMixedException, HeuristicRollbackException, SecurityException, SystemException {
            throw Log.log.notActive();
        }

        void rollback(final AtomicReference<State> stateRef) throws IllegalStateException, SystemException {
            throw Log.log.notActive();
        }

        void setRollbackOnly(final AtomicReference<State> stateRef) throws IllegalStateException, SystemException {
            if (status != Status.STATUS_ROLLING_BACK) {
                throw Log.log.notActive();
            }
        }

        int getStatus() {
            return status;
        }

        void registerSynchronization(final Synchronization sync) throws RollbackException, IllegalStateException, SystemException {
            throw Log.log.notActive();
        }

        void registerInterposedSynchronization(final Synchronization sync) throws IllegalStateException {
            throw Log.log.notActive();
        }
    }
}

