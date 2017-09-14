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

package org.wildfly.transaction.client._private;

import java.io.IOException;
import java.net.URI;
import java.security.Permission;
import java.util.ServiceConfigurationError;

import javax.transaction.HeuristicCommitException;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.InvalidTransactionException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.Field;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;
import org.jboss.remoting3.Endpoint;
import org.wildfly.transaction.client.DelayedEnlistmentException;
import org.wildfly.transaction.client.SynchronizationException;

/**
 * Log messages.
 */
@MessageLogger(projectCode = "WFTXN", length = 4)
public interface Log extends BasicLogger {
    Log log = Logger.getMessageLogger(Log.class, "org.wildfly.transaction.client");

    // Strings

    @Message(value = "Subordinate XAResource at %s")
    String subordinateXaResource(URI location);

    // Debug

    @LogMessage(level = Logger.Level.DEBUG)
    @Message(value = "Failed to configure a remote transaction service provider")
    void serviceConfigurationFailed(@Cause ServiceConfigurationError e);

    @LogMessage(level = Logger.Level.DEBUG)
    @Message(value = "Closing the recovery stream after recovery failed threw an exception")
    void recoverySuppressedException(@Cause XAException e);

    // Trace

    @LogMessage(level = Logger.Level.TRACE)
    @Message(value = "Got exception on inbound message")
    void inboundException(@Cause Throwable e);

    @LogMessage(level = Logger.Level.TRACE)
    @Message(value = "Got exception on outbound message")
    void outboundException(@Cause Throwable e);

    @LogMessage(level = Logger.Level.TRACE)
    @Message(value = "Failure on running doRecover during initialization")
    void doRecoverFailureOnIntialization(@Cause Throwable e);

    // Regular messages

    @Message(id = 0, value = "No transaction associated with the current thread")
    IllegalStateException noTransaction();

    @Message(id = 1, value = "A transaction is already in progress")
    NotSupportedException nestedNotSupported();

    @Message(id = 2, value = "Transaction is not a supported instance: %s")
    InvalidTransactionException notSupportedTransaction(Transaction transaction);

    @Message(id = 3, value = "Invalid transaction location URI (must be absolute): %s")
    IllegalArgumentException invalidTransactionLocationUri(URI uri);

    @Message(id = 4, value = "No transaction provider installed for URI: %s")
    IllegalArgumentException noProviderForUri(URI uri);

    @Message(id = 5, value = "Transaction not associated with this provider")
    InvalidTransactionException transactionNotAssociatedWithThisProvider();

    @Message(id = 6, value = "Negative transaction timeout provided")
    SystemException negativeTxnTimeout();

    @Message(id = 7, value = "A transaction is already associated with the current thread")
    IllegalStateException alreadyAssociated();

    @Message(id = 8, value = "Cannot register a synchronization on a remote transaction")
    UnsupportedOperationException registerSynchRemoteTransaction();

    @Message(id = 9, value = "Cannot enlist or delist resources on a remote transaction")
    UnsupportedOperationException enlistDelistRemoteTransaction();

    @Message(id = 10, value = "Failed to receive protocol message from remote peer")
    SystemException failedToReceive(@Cause IOException e);

    @Message(id = 11, value = "Failed to send protocol message to remote peer")
    SystemException failedToSend(@Cause Exception e);

    @Message(id = 12, value = "The peer threw a SystemException; see peer logs for more information")
    SystemException peerSystemException();

    @Message(id = 13, value = "The peer threw a SecurityException; see peer logs for more information")
    SecurityException peerSecurityException();

    @Message(id = 14, value = "An unexpected protocol error occurred")
    SystemException protocolError();

    @Message(id = 15, value = "The protocol operation was interrupted locally")
    SystemException operationInterrupted();

    @Message(id = 16, value = "The remote peer rolled back the transaction")
    RollbackException transactionRolledBackByPeer();

    @Message(id = 17, value = "Rollback-only transaction rolled back")
    RollbackException rollbackOnlyRollback();

    @Message(id = 18, value = "Invalid transaction state")
    IllegalStateException invalidTxnState();

    @Message(id = 19, value = "The peer threw a HeuristicMixedException; see peer logs for more information")
    HeuristicMixedException peerHeuristicMixedException();

    @Message(id = 20, value = "The peer threw a HeuristicRollbackException; see peer logs for more information")
    HeuristicRollbackException peerHeuristicRollbackException();

    @Message(id = 21, value = "Failed to acquire a connection for this operation")
    SystemException failedToAcquireConnection(@Cause IOException reason);

    @Message(id = 22, value = "The resource manager for remote connection to %s was already enlisted in a transaction")
    XAException duplicateEnlistment(@Field int errorCode, URI uri);

    @Message(id = 23, value = "Invalid flag value")
    IllegalArgumentException invalidFlags();

    @Message(id = 24, value = "Duplicate transaction encountered for destination %s, transaction ID %s")
    XAException duplicateTransaction(@Field int errorCode, URI uri, Xid xid);

    @Message(id = 25, value = "Failed to receive protocol message from remote peer")
    XAException failedToReceiveXA(@Cause IOException e, @Field int errorCode);

    @Message(id = 26, value = "Failed to send protocol message to remote peer")
    XAException failedToSendXA(@Cause Exception e, @Field int errorCode);

    @Message(id = 27, value = "The protocol operation was interrupted locally")
    XAException operationInterruptedXA(@Field int errorCode);

    @Message(id = 28, value = "An unexpected protocol error occurred")
    XAException protocolErrorXA(@Field int errorCode);

    @Message(id = 29, value = "The peer threw an XA exception")
    XAException peerXaException(@Field int errorCode);

    @Message(id = 30, value = "Invalid handle type; expected %s, actually received %s")
    IllegalArgumentException invalidHandleType(Class<?> expected, Class<?> actual);

    @Message(id = 31, value = "Commit not allowed on imported transaction")
    SystemException commitOnImported();

    @Message(id = 32, value = "Rollback not allowed on imported transaction")
    SystemException rollbackOnImported();

    @Message(id = 33, value = "Multiple remote transaction providers registered on endpoint: %s")
    IllegalStateException multipleProvidersRegistered(Endpoint e);

    @Message(id = 34, value = "Failed to acquire a connection for this operation")
    XAException failedToAcquireConnectionXA(@Cause IOException e, @Field int errorCode);

    @Message(id = 35, value = "Invalid handle type requested; expected a subtype of Transaction (non-inclusive), got %s")
    IllegalArgumentException invalidHandleTypeRequested(Class<?> type);

    @Message(id = 36, value = "Transaction operation failed due to thread interruption")
    XAException interruptedXA(@Field int errorCode);

    @Message(id = 37, value = "No transaction provider associated with the current thread")
    IllegalStateException noTransactionProvider();

    @Message(id = 38, value = "No local transaction provider associated with the current thread")
    IllegalStateException noLocalTransactionProvider();

    @Message(id = 39, value = "Invalid null transaction")
    NullPointerException nullTransaction();

    @LogMessage(level = Logger.Level.DEBUG)
    @Message(id = 40, value = "Rollback failed unexpectedly")
    void rollbackFailed(@Cause Throwable e);

    @Message(id = 41, value = "No provider interface matching %s is available from the transaction provider")
    IllegalStateException noProviderInterface(Class<?> clazz);

    @Message(id = 42, value = "Connection to remote transaction service failed")
    SystemException connectionFailed(@Cause Throwable cause);

    @Message(id = 43, value = "Connection to remote transaction service interrupted")
    SystemException connectionInterrupted();

    @Message(id = 44, value = "Unknown response received from peer")
    SystemException unknownResponse();

    @Message(id = 45, value = "Failed to receive a response from peer")
    SystemException responseFailed(@Cause IOException cause);

    @Message(id = 46, value = "The peer threw an IllegalStateException; see peer logs for more information")
    IllegalStateException peerIllegalStateException();

    @Message(id = 47, value = "Unknown XA response received from peer")
    XAException unknownResponseXa(@Field int errorCode);

    @Message(id = 48, value = "Failed to receive an XA response from peer")
    XAException responseFailedXa(@Cause IOException cause, @Field int errorCode);

    @Message(id = 49, value = "Negative transaction timeout provided")
    XAException negativeTxnTimeoutXa(@Field int errorCode);

    @Message(id = 50, value = "Unrecognized parameter with ID 0x%02x received")
    XAException unrecognizedParameter(@Field int errorCode, int id);

    @Message(id = 51, value = "Expected parameter with ID 0x%02x, got parameter with ID 0x%02x instead")
    SystemException expectedParameter(int expected, int actual);

    @Message(id = 52, value = "Expected parameter with ID 0x%02x, got parameter with ID 0x%02x instead")
    XAException expectedParameterXa(@Field int errorCode, int expected, int actual);

    @Message(id = 53, value = "Delayed enlistment has failed")
    DelayedEnlistmentException delayedEnlistmentFailed(@Cause Throwable cause);

    @Message(id = 54, value = "Before-completion failed for resource %s")
    SynchronizationException beforeCompletionFailed(@Cause Throwable cause, XAResource resource);

    @Message(id = 55, value = "Two-phase operation on single-phase transaction")
    XAException onePhaseUserTransaction(@Field int errorCode);

    @Message(id = 56, value = "Unknown provider for remote transactions with URI scheme \"%s\"")
    IllegalArgumentException unknownProvider(String scheme);

    @Message(id = 57, value = "Attempted to outflow the same transaction from two different transaction managers")
    SystemException outflowAcrossTransactionManagers();

    @Message(id = 58, value = "This delayed enlistment handle was already enlisted")
    IllegalStateException alreadyEnlisted();

    @Message(id = 59, value = "This delayed enlistment handle was already forgotten")
    IllegalStateException alreadyForgotten();

    @Message(id = 60, value = "Transaction timed out")
    XAException transactionTimedOut(@Field int errorCode);

    @Message(id = 61, value = "Transaction is marked rollback-only")
    RollbackException markedRollbackOnly();

    @Message(id = 62, value = "Transaction is not active")
    IllegalStateException notActive();

    @Message(id = 63, value = "Provider created a null transaction")
    IllegalStateException providerCreatedNullTransaction();

    @Message(id = 64, value = "Invalid connection endpoint provided")
    IllegalArgumentException invalidConnectionEndpoint();

    @Message(id = 65, value = "No transaction for ID %d")
    SystemException noTransactionForId(int id);

    @Message(id = 66, value = "Failed to set transaction as rollback-only")
    XAException rollbackOnlyFailed(@Field int errorCode, @Cause SystemException e);

    @Message(id = 67, value = "Transaction is not active")
    XAException notActiveXA(@Field int errorCode);

    @Message(id = 68, value = "Subordinate enlistment failed for unknown reason")
    SystemException couldNotEnlist();

    @Message(id = 69, value = "Connection does not match the transaction; the connection may have closed")
    InvalidTransactionException invalidTransactionConnection();

    @Message(id = 70, value = "No such transaction")
    XAException noTransactionXa(@Field int errorCode);

    @Message(id = 71, value = "An unexpected failure condition occurred")
    IllegalStateException unexpectedFailure(@Cause Throwable e);

    @Message(id = 72, value = "No local transaction provider node name specified in the transaction manager environment")
    IllegalStateException noLocalTransactionProviderNodeName();

    @Message(id = 73, value = "Unexpected provider transaction mismatch; expected %s, got %s")
    IllegalStateException unexpectedProviderTransactionMismatch(Transaction expected, Transaction actual);

    @Message(id = 74, value = "Heuristic-mixed outcome")
    XAException heuristicMixedXa(@Field int errorCode, @Cause HeuristicMixedException cause);

    @Message(id = 75, value = "Heuristic-commit outcome")
    XAException heuristicCommitXa(@Field int errorCode, @Cause HeuristicCommitException cause);

    @Message(id = 76, value = "Transaction rolled back")
    XAException rollbackXa(@Field int errorCode, @Cause RollbackException cause);

    @Message(id = 77, value = "Heuristic-rollback outcome")
    XAException heuristicRollbackXa(@Field int errorCode, @Cause HeuristicRollbackException cause);

    @Message(id = 78, value = "Invalid transaction state")
    XAException illegalStateXa(@Field int errorCode, @Cause IllegalStateException cause);

    @Message(id = 79, value = "An unexpected resource manager error occurred")
    XAException resourceManagerErrorXa(@Field int errorCode, @Cause Throwable cause);

    @Message(id = 80, value = "Operation not allowed on non-imported transaction")
    XAException notImportedXa(@Field int errorCode);

    @Message(id = 81, value = "Invalid transaction state for operation")
    XAException invalidTxStateXa(@Field int errorCode);

    @Message(id = 82, value = "Cannot import a new transaction on a suspended server")
    SystemException suspendedCannotCreateNew();

    @Message(id = 83, value = "Cannot import a new transaction on a suspended server")
    XAException suspendedCannotImportXa(@Field int errorCode);

    @Message(id = 84, value = "UserTransaction access is forbidden in the current context")
    IllegalStateException forbiddenContextForUserTransaction();

    @Message(id = 85, value = "Operation failed with an unexpected exception type")
    SystemException unexpectedException(@Cause Exception e);

    @Message(id = 86, value = "Unexpected transaction type encountered; expected %s but encountered %s")
    IllegalStateException unknownTransactionType(Class<?> expectedType, Class<?> actualType);

    @Message(id = 87, value = "Unknown transaction manager type %s")
    IllegalArgumentException unknownTransactionManagerType(Class<?> actualType);

    @Message(id = 88, value = "User %s does not have permission %s")
    SecurityException noPermission(String user, Permission permission);

    @Message(id = 89, value = "Failed to configure transaction timeout of %d")
    SystemException setTimeoutFailed(int timeout, @Cause XAException e);

    @Message(id = 90, value = "Cannot assign location \"%s\" to transaction because it is already located at \"%s\"")
    IllegalStateException locationAlreadyInitialized(URI newLocation, URI oldLocation);
}
