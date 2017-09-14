/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015 Red Hat, Inc., and individual contributors
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
package org.wildfly.transaction.client.naming.txn;

import java.util.Arrays;
import java.util.Collections;

import javax.naming.Binding;
import javax.naming.Name;
import javax.naming.NameClassPair;
import javax.naming.NamingException;
import javax.transaction.TransactionManager;
import javax.transaction.TransactionSynchronizationRegistry;
import javax.transaction.UserTransaction;

import org.wildfly.naming.client.AbstractContext;
import org.wildfly.naming.client.CloseableNamingEnumeration;
import org.wildfly.naming.client.NamingProvider;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.transaction.client.ContextTransactionManager;
import org.wildfly.transaction.client.ContextTransactionSynchronizationRegistry;
import org.wildfly.transaction.client.LocalUserTransaction;
import org.wildfly.transaction.client.RemoteTransactionContext;
import org.wildfly.transaction.client.RemoteUserTransaction;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
class TxnNamingContext extends AbstractContext {

    private static final String TRANSACTION_MANAGER = "TransactionManager";
    private static final String USER_TRANSACTION = "UserTransaction";
    private static final String REMOTE_USER_TRANSACTION = "RemoteUserTransaction";
    private static final String LOCAL_USER_TRANSACTION = "LocalUserTransaction";
    private static final String TRANSACTION_SYNCHRONIZATION_REGISTRY = "TransactionSynchronizationRegistry";
    private final NamingProvider namingProvider;
    private final RemoteUserTransaction remoteUserTransaction = getRemoteUserTransaction();

    TxnNamingContext(final NamingProvider namingProvider) {
        this.namingProvider = namingProvider;
    }

    protected Object lookupNative(final Name name) throws NamingException {
        final String str = name.get(0);
        switch (str) {
            case USER_TRANSACTION: {
                if (namingProvider != null) {
                    return remoteUserTransaction;
                } else {
                    return LocalUserTransaction.getInstance();
                }
            }
            case REMOTE_USER_TRANSACTION: {
                return remoteUserTransaction;
            }
            case LOCAL_USER_TRANSACTION: {
                return LocalUserTransaction.getInstance();
            }
            case TRANSACTION_MANAGER: {
                if (namingProvider == null) {
                    return ContextTransactionManager.getInstance();
                }
                break;
            }
            case TRANSACTION_SYNCHRONIZATION_REGISTRY: {
                if (namingProvider == null) {
                    return ContextTransactionSynchronizationRegistry.getInstance();
                }
                break;
            }
        }
        throw nameNotFound(name);
    }

    protected Object lookupLinkNative(final Name name) throws NamingException {
        return lookupNative(name);
    }

    protected CloseableNamingEnumeration<NameClassPair> listNative(final Name name) throws NamingException {
        if (!name.isEmpty()) {
            throw nameNotFound(name);
        }
        return CloseableNamingEnumeration.fromIterable(
            namingProvider == null ?
                Arrays.asList(
                    nameClassPair(UserTransaction.class),
                    nameClassPair(RemoteUserTransaction.class),
                    nameClassPair(LocalUserTransaction.class),
                    nameClassPair(TransactionManager.class),
                    nameClassPair(TransactionSynchronizationRegistry.class)
                ) :
                Collections.singleton(
                    nameClassPair(UserTransaction.class)
                )
        );
    }

    protected CloseableNamingEnumeration<Binding> listBindingsNative(final Name name) throws NamingException {
        if (!name.isEmpty()) {
            throw nameNotFound(name);
        }
        return CloseableNamingEnumeration.fromIterable(
            Arrays.asList(
                binding(UserTransaction.class, namingProvider != null ? remoteUserTransaction : LocalUserTransaction.getInstance()),
                binding(RemoteUserTransaction.class, remoteUserTransaction),
                binding(LocalUserTransaction.class, LocalUserTransaction.getInstance()),
                binding(TransactionManager.class, ContextTransactionManager.getInstance()),
                binding(TransactionSynchronizationRegistry.class, ContextTransactionSynchronizationRegistry.getInstance())
            )
        );
    }

    private NameClassPair nameClassPair(Class<?> clazz) {
        return new ReadOnlyNameClassPair(clazz.getSimpleName(), clazz.getName(), "txn:" + clazz.getSimpleName());
    }

    private <T> Binding binding(Class<T> clazz, T content) {
        return new ReadOnlyBinding(clazz.getSimpleName(), clazz.getName(), content, "txn:" + clazz.getSimpleName());
    }

    private static RemoteUserTransaction getRemoteUserTransaction() {
        AuthenticationContext context = AuthenticationContext.captureCurrent();
        final NamingProvider currentNamingProvider = NamingProvider.getCurrentNamingProvider();
        if (currentNamingProvider != null) {
            context = currentNamingProvider.getProviderEnvironment().getAuthenticationContextSupplier().get();
        }
        return context.runFunction(RemoteTransactionContext::getUserTransaction, RemoteTransactionContext.getInstance());
    }

    @Override
    public void close() throws NamingException {
    }

    @Override
    public String getNameInNamespace() throws NamingException {
        return "";
    }
}
