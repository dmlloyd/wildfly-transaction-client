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

import java.io.Serializable;

import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.transaction.UserTransaction;

import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.transaction.client._private.Log;

/**
 * A remote {@code UserTransaction} which controls the transaction state of a remote system.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class RemoteUserTransaction implements UserTransaction, Serializable {
    private static final long serialVersionUID = 8612109476723652825L;

    private final ThreadLocal<State> stateRef = ThreadLocal.withInitial(State::new);
    private final AuthenticationContext authenticationContext;

    RemoteUserTransaction(final AuthenticationContext authenticationContext) {
        this.authenticationContext = authenticationContext;
    }

    public void begin() throws NotSupportedException, SystemException {
        final ContextTransactionManager transactionManager = ContextTransactionManager.getInstance();
        if (transactionManager.getTransaction() != null) {
            throw Log.log.nestedNotSupported();
        }
        final RemoteTransactionContext context = RemoteTransactionContext.getInstancePrivate();
        int timeout = stateRef.get().timeout;
        if (timeout == 0) timeout = ContextTransactionManager.getGlobalDefaultTransactionTimeout();
        transactionManager.resume(context.notifyCreationListeners(new RemoteTransaction(authenticationContext, timeout), CreationListener.CreatedBy.USER_TRANSACTION));
    }

    public void commit() throws RollbackException, HeuristicMixedException, HeuristicRollbackException, SecurityException, IllegalStateException, SystemException {
        ContextTransactionManager transactionManager = ContextTransactionManager.getInstance();
        final RemoteTransaction remoteTransaction = getMatchingTransaction();
        if (remoteTransaction == null) {
            throw Log.log.invalidTxnState();
        } else {
            transactionManager.commit();
        }
    }

    public void rollback() throws IllegalStateException, SecurityException, SystemException {
        ContextTransactionManager transactionManager = ContextTransactionManager.getInstance();
        final RemoteTransaction remoteTransaction = getMatchingTransaction();
        if (remoteTransaction == null) {
            throw Log.log.invalidTxnState();
        } else {
            transactionManager.rollback();
        }
    }

    public void setRollbackOnly() throws IllegalStateException, SystemException {
        ContextTransactionManager transactionManager = ContextTransactionManager.getInstance();
        final RemoteTransaction remoteTransaction = getMatchingTransaction();
        if (remoteTransaction == null) {
            throw Log.log.noTransaction();
        } else {
            transactionManager.setRollbackOnly();
        }
    }

    public int getStatus() {
        final RemoteTransaction remoteTransaction = getMatchingTransaction();
        return remoteTransaction == null ? Status.STATUS_NO_TRANSACTION : remoteTransaction.getStatus();
    }

    RemoteTransaction getMatchingTransaction() {
        final AbstractTransaction transaction = ContextTransactionManager.getInstance().getTransaction();
        return transaction instanceof RemoteTransaction ? (RemoteTransaction) transaction : null;
    }

    public void setTransactionTimeout(final int seconds) throws SystemException {
        if (seconds < 0) throw Log.log.negativeTxnTimeout();
        stateRef.get().timeout = seconds;
    }

    public int getTransactionTimeout() {
        final int timeout = stateRef.get().timeout;
        return timeout == 0 ? ContextTransactionManager.getGlobalDefaultTransactionTimeout() : timeout;
    }

    Object writeReplace() {
        return new SerializedUserTransaction();
    }

    static final class State {
        int timeout = 0;

        State() {
        }
    }
}
