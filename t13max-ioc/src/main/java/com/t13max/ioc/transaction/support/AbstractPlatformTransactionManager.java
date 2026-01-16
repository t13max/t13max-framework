package com.t13max.ioc.transaction.support;

import com.t13max.ioc.utils.Assert;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @Author: t13max
 * @Since: 20:37 2026/1/16
 */
public class AbstractPlatformTransactionManager implements PlatformTransactionManager, ConfigurableTransactionManager, Serializable {

    public static final int SYNCHRONIZATION_ALWAYS = 0;

    public static final int SYNCHRONIZATION_ON_ACTUAL_TRANSACTION = 1;    public static final int SYNCHRONIZATION_NEVER = 2;    static final Map<String, Integer> constants = Map.of(
            "SYNCHRONIZATION_ALWAYS", SYNCHRONIZATION_ALWAYS,
            "SYNCHRONIZATION_ON_ACTUAL_TRANSACTION", SYNCHRONIZATION_ON_ACTUAL_TRANSACTION,
            "SYNCHRONIZATION_NEVER", SYNCHRONIZATION_NEVER
    );

    protected transient Logger logger = LogManager.getLogger(getClass());

    private int transactionSynchronization = SYNCHRONIZATION_ALWAYS;

    private int defaultTimeout = TransactionDefinition.TIMEOUT_DEFAULT;

    private boolean nestedTransactionAllowed = false;

    private boolean validateExistingTransaction = false;

    private boolean globalRollbackOnParticipationFailure = true;

    private boolean failEarlyOnGlobalRollbackOnly = false;

    private boolean rollbackOnCommitFailure = false;

    private Collection<TransactionExecutionListener> transactionExecutionListeners = new ArrayList<>();

    public final void setTransactionSynchronizationName(String constantName) {
        Assert.hasText(constantName, "'constantName' must not be null or blank");
        Integer transactionSynchronization = constants.get(constantName);
        Assert.notNull(transactionSynchronization, "Only transaction synchronization constants allowed");
        this.transactionSynchronization = transactionSynchronization;
    }
    public final void setTransactionSynchronization(int transactionSynchronization) {
        this.transactionSynchronization = transactionSynchronization;
    }
    public final int getTransactionSynchronization() {
        return this.transactionSynchronization;
    }
    public final void setDefaultTimeout(int defaultTimeout) {
        if (defaultTimeout < TransactionDefinition.TIMEOUT_DEFAULT) {
            throw new InvalidTimeoutException("Invalid default timeout", defaultTimeout);
        }
        this.defaultTimeout = defaultTimeout;
    }
    public final int getDefaultTimeout() {
        return this.defaultTimeout;
    }
    public final void setNestedTransactionAllowed(boolean nestedTransactionAllowed) {
        this.nestedTransactionAllowed = nestedTransactionAllowed;
    }
    public final boolean isNestedTransactionAllowed() {
        return this.nestedTransactionAllowed;
    }
    public final void setValidateExistingTransaction(boolean validateExistingTransaction) {
        this.validateExistingTransaction = validateExistingTransaction;
    }
    public final boolean isValidateExistingTransaction() {
        return this.validateExistingTransaction;
    }
    public final void setGlobalRollbackOnParticipationFailure(boolean globalRollbackOnParticipationFailure) {
        this.globalRollbackOnParticipationFailure = globalRollbackOnParticipationFailure;
    }
    public final boolean isGlobalRollbackOnParticipationFailure() {
        return this.globalRollbackOnParticipationFailure;
    }
    public final void setFailEarlyOnGlobalRollbackOnly(boolean failEarlyOnGlobalRollbackOnly) {
        this.failEarlyOnGlobalRollbackOnly = failEarlyOnGlobalRollbackOnly;
    }
    public final boolean isFailEarlyOnGlobalRollbackOnly() {
        return this.failEarlyOnGlobalRollbackOnly;
    }
    public final void setRollbackOnCommitFailure(boolean rollbackOnCommitFailure) {
        this.rollbackOnCommitFailure = rollbackOnCommitFailure;
    }
    public final boolean isRollbackOnCommitFailure() {
        return this.rollbackOnCommitFailure;
    }

    @Override
    public final void setTransactionExecutionListeners(Collection<TransactionExecutionListener> listeners) {
        this.transactionExecutionListeners = listeners;
    }

    @Override
    public final Collection<TransactionExecutionListener> getTransactionExecutionListeners() {
        return this.transactionExecutionListeners;
    }


    //---------------------------------------------------------------------
    // Implementation of PlatformTransactionManager
    //---------------------------------------------------------------------
    @Override
    public final TransactionStatus getTransaction( TransactionDefinition definition)
            throws TransactionException {

        // Use defaults if no transaction definition given.
        TransactionDefinition def = (definition != null ? definition : TransactionDefinition.withDefaults());

        Object transaction = doGetTransaction();
        boolean debugEnabled = logger.isDebugEnabled();

        if (isExistingTransaction(transaction)) {
            // Existing transaction found -> check propagation behavior to find out how to behave.
            return handleExistingTransaction(def, transaction, debugEnabled);
        }

        // Check definition settings for new transaction.
        if (def.getTimeout() < TransactionDefinition.TIMEOUT_DEFAULT) {
            throw new InvalidTimeoutException("Invalid transaction timeout", def.getTimeout());
        }

        // No existing transaction found -> check propagation behavior to find out how to proceed.
        if (def.getPropagationBehavior() == TransactionDefinition.PROPAGATION_MANDATORY) {
            throw new IllegalTransactionStateException(
                    "No existing transaction found for transaction marked with propagation 'mandatory'");
        }
        else if (def.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRED ||
                def.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW ||
                def.getPropagationBehavior() == TransactionDefinition.PROPAGATION_NESTED) {
            SuspendedResourcesHolder suspendedResources = suspend(null);
            if (debugEnabled) {
                logger.debug("Creating new transaction with name [" + def.getName() + "]: " + def);
            }
            try {
                return startTransaction(def, transaction, false, debugEnabled, suspendedResources);
            }
            catch (RuntimeException | Error ex) {
                resume(null, suspendedResources);
                throw ex;
            }
        }
        else {
            // Create "empty" transaction: no actual transaction, but potentially synchronization.
            if (def.getIsolationLevel() != TransactionDefinition.ISOLATION_DEFAULT && logger.isWarnEnabled()) {
                logger.warn("Custom isolation level specified but no actual transaction initiated; " +
                        "isolation level will effectively be ignored: " + def);
            }
            boolean newSynchronization = (getTransactionSynchronization() == SYNCHRONIZATION_ALWAYS);
            return prepareTransactionStatus(def, null, true, newSynchronization, debugEnabled, null);
        }
    }
    private TransactionStatus handleExistingTransaction(
            TransactionDefinition definition, Object transaction, boolean debugEnabled)
            throws TransactionException {

        if (definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_NEVER) {
            throw new IllegalTransactionStateException(
                    "Existing transaction found for transaction marked with propagation 'never'");
        }

        if (definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_NOT_SUPPORTED) {
            if (debugEnabled) {
                logger.debug("Suspending current transaction");
            }
            Object suspendedResources = suspend(transaction);
            boolean newSynchronization = (getTransactionSynchronization() == SYNCHRONIZATION_ALWAYS);
            return prepareTransactionStatus(
                    definition, null, false, newSynchronization, debugEnabled, suspendedResources);
        }

        if (definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW) {
            if (debugEnabled) {
                logger.debug("Suspending current transaction, creating new transaction with name [" +
                        definition.getName() + "]");
            }
            SuspendedResourcesHolder suspendedResources = suspend(transaction);
            try {
                return startTransaction(definition, transaction, false, debugEnabled, suspendedResources);
            }
            catch (RuntimeException | Error beginEx) {
                resumeAfterBeginException(transaction, suspendedResources, beginEx);
                throw beginEx;
            }
        }

        if (definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_NESTED) {
            if (!isNestedTransactionAllowed()) {
                throw new NestedTransactionNotSupportedException(
                        "Transaction manager does not allow nested transactions by default - " +
                                "specify 'nestedTransactionAllowed' property with value 'true'");
            }
            if (debugEnabled) {
                logger.debug("Creating nested transaction with name [" + definition.getName() + "]");
            }
            if (useSavepointForNestedTransaction()) {
                // Create savepoint within existing Spring-managed transaction,
                // through the SavepointManager API implemented by TransactionStatus.
                // Usually uses JDBC savepoints. Never activates Spring synchronization.
                DefaultTransactionStatus status = newTransactionStatus(
                        definition, transaction, false, false, true, debugEnabled, null);
                this.transactionExecutionListeners.forEach(listener -> listener.beforeBegin(status));
                try {
                    status.createAndHoldSavepoint();
                }
                catch (RuntimeException | Error ex) {
                    this.transactionExecutionListeners.forEach(listener -> listener.afterBegin(status, ex));
                    throw ex;
                }
                this.transactionExecutionListeners.forEach(listener -> listener.afterBegin(status, null));
                return status;
            }
            else {
                // Nested transaction through nested begin and commit/rollback calls.
                // Usually only for JTA: Spring synchronization might get activated here
                // in case of a pre-existing JTA transaction.
                return startTransaction(definition, transaction, true, debugEnabled, null);
            }
        }

        // PROPAGATION_REQUIRED, PROPAGATION_SUPPORTS, PROPAGATION_MANDATORY:
        // regular participation in existing transaction.
        if (debugEnabled) {
            logger.debug("Participating in existing transaction");
        }
        if (isValidateExistingTransaction()) {
            if (definition.getIsolationLevel() != TransactionDefinition.ISOLATION_DEFAULT) {
                Integer currentIsolationLevel = TransactionSynchronizationManager.getCurrentTransactionIsolationLevel();
                if (currentIsolationLevel == null || currentIsolationLevel != definition.getIsolationLevel()) {
                    throw new IllegalTransactionStateException("Participating transaction with definition [" +
                            definition + "] specifies isolation level which is incompatible with existing transaction: " +
                            (currentIsolationLevel != null ?
                                    DefaultTransactionDefinition.getIsolationLevelName(currentIsolationLevel) :
                                    "(unknown)"));
                }
            }
            if (!definition.isReadOnly()) {
                if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
                    throw new IllegalTransactionStateException("Participating transaction with definition [" +
                            definition + "] is not marked as read-only but existing transaction is");
                }
            }
        }
        boolean newSynchronization = (getTransactionSynchronization() != SYNCHRONIZATION_NEVER);
        return prepareTransactionStatus(definition, transaction, false, newSynchronization, debugEnabled, null);
    }
    private TransactionStatus startTransaction(TransactionDefinition definition, Object transaction,
                                               boolean nested, boolean debugEnabled,  SuspendedResourcesHolder suspendedResources) {

        boolean newSynchronization = (getTransactionSynchronization() != SYNCHRONIZATION_NEVER);
        DefaultTransactionStatus status = newTransactionStatus(
                definition, transaction, true, newSynchronization, nested, debugEnabled, suspendedResources);
        this.transactionExecutionListeners.forEach(listener -> listener.beforeBegin(status));
        try {
            doBegin(transaction, definition);
        }
        catch (RuntimeException | Error ex) {
            this.transactionExecutionListeners.forEach(listener -> listener.afterBegin(status, ex));
            throw ex;
        }
        prepareSynchronization(status, definition);
        this.transactionExecutionListeners.forEach(listener -> listener.afterBegin(status, null));
        return status;
    }
    private DefaultTransactionStatus prepareTransactionStatus(
            TransactionDefinition definition,  Object transaction, boolean newTransaction,
            boolean newSynchronization, boolean debug,  Object suspendedResources) {

        DefaultTransactionStatus status = newTransactionStatus(
                definition, transaction, newTransaction, newSynchronization, false, debug, suspendedResources);
        prepareSynchronization(status, definition);
        return status;
    }
    private DefaultTransactionStatus newTransactionStatus(
            TransactionDefinition definition,  Object transaction, boolean newTransaction,
            boolean newSynchronization, boolean nested, boolean debug,  Object suspendedResources) {

        boolean actualNewSynchronization = newSynchronization &&
                !TransactionSynchronizationManager.isSynchronizationActive();
        return new DefaultTransactionStatus(definition.getName(), transaction, newTransaction,
                actualNewSynchronization, nested, definition.isReadOnly(), debug, suspendedResources);
    }
    protected void prepareSynchronization(DefaultTransactionStatus status, TransactionDefinition definition) {
        if (status.isNewSynchronization()) {
            TransactionSynchronizationManager.setActualTransactionActive(status.hasTransaction());
            TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(
                    definition.getIsolationLevel() != TransactionDefinition.ISOLATION_DEFAULT ?
                            definition.getIsolationLevel() : null);
            TransactionSynchronizationManager.setCurrentTransactionReadOnly(definition.isReadOnly());
            TransactionSynchronizationManager.setCurrentTransactionName(definition.getName());
            TransactionSynchronizationManager.initSynchronization();
        }
    }
    protected int determineTimeout(TransactionDefinition definition) {
        if (definition.getTimeout() != TransactionDefinition.TIMEOUT_DEFAULT) {
            return definition.getTimeout();
        }
        return getDefaultTimeout();
    }

    protected final  SuspendedResourcesHolder suspend( Object transaction) throws TransactionException {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            List<TransactionSynchronization> suspendedSynchronizations = doSuspendSynchronization();
            try {
                Object suspendedResources = null;
                if (transaction != null) {
                    suspendedResources = doSuspend(transaction);
                }
                String name = TransactionSynchronizationManager.getCurrentTransactionName();
                TransactionSynchronizationManager.setCurrentTransactionName(null);
                boolean readOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
                TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
                Integer isolationLevel = TransactionSynchronizationManager.getCurrentTransactionIsolationLevel();
                TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(null);
                boolean wasActive = TransactionSynchronizationManager.isActualTransactionActive();
                TransactionSynchronizationManager.setActualTransactionActive(false);
                return new SuspendedResourcesHolder(
                        suspendedResources, suspendedSynchronizations, name, readOnly, isolationLevel, wasActive);
            }
            catch (RuntimeException | Error ex) {
                // doSuspend failed - original transaction is still active...
                doResumeSynchronization(suspendedSynchronizations);
                throw ex;
            }
        }
        else if (transaction != null) {
            // Transaction active but no synchronization active.
            Object suspendedResources = doSuspend(transaction);
            return new SuspendedResourcesHolder(suspendedResources);
        }
        else {
            // Neither transaction nor synchronization active.
            return null;
        }
    }
    protected final void resume( Object transaction,  SuspendedResourcesHolder resourcesHolder)
            throws TransactionException {

        if (resourcesHolder != null) {
            Object suspendedResources = resourcesHolder.suspendedResources;
            if (suspendedResources != null) {
                doResume(transaction, suspendedResources);
            }
            List<TransactionSynchronization> suspendedSynchronizations = resourcesHolder.suspendedSynchronizations;
            if (suspendedSynchronizations != null) {
                TransactionSynchronizationManager.setActualTransactionActive(resourcesHolder.wasActive);
                TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(resourcesHolder.isolationLevel);
                TransactionSynchronizationManager.setCurrentTransactionReadOnly(resourcesHolder.readOnly);
                TransactionSynchronizationManager.setCurrentTransactionName(resourcesHolder.name);
                doResumeSynchronization(suspendedSynchronizations);
            }
        }
    }
    private void resumeAfterBeginException(
            Object transaction,  SuspendedResourcesHolder suspendedResources, Throwable beginEx) {

        try {
            resume(transaction, suspendedResources);
        }
        catch (RuntimeException | Error resumeEx) {
            String exMessage = "Inner transaction begin exception overridden by outer transaction resume exception";
            logger.error(exMessage, beginEx);
            throw resumeEx;
        }
    }
    private List<TransactionSynchronization> doSuspendSynchronization() {
        List<TransactionSynchronization> suspendedSynchronizations =
                TransactionSynchronizationManager.getSynchronizations();
        for (TransactionSynchronization synchronization : suspendedSynchronizations) {
            synchronization.suspend();
        }
        TransactionSynchronizationManager.clearSynchronization();
        return suspendedSynchronizations;
    }
    private void doResumeSynchronization(List<TransactionSynchronization> suspendedSynchronizations) {
        TransactionSynchronizationManager.initSynchronization();
        for (TransactionSynchronization synchronization : suspendedSynchronizations) {
            synchronization.resume();
            TransactionSynchronizationManager.registerSynchronization(synchronization);
        }
    }

    @Override
    public final void commit(TransactionStatus status) throws TransactionException {
        if (status.isCompleted()) {
            throw new IllegalTransactionStateException(
                    "Transaction is already completed - do not call commit or rollback more than once per transaction");
        }

        DefaultTransactionStatus defStatus = (DefaultTransactionStatus) status;
        if (defStatus.isLocalRollbackOnly()) {
            if (defStatus.isDebug()) {
                logger.debug("Transactional code has requested rollback");
            }
            processRollback(defStatus, false);
            return;
        }

        if (!shouldCommitOnGlobalRollbackOnly() && defStatus.isGlobalRollbackOnly()) {
            if (defStatus.isDebug()) {
                logger.debug("Global transaction is marked as rollback-only but transactional code requested commit");
            }
            processRollback(defStatus, true);
            return;
        }

        processCommit(defStatus);
    }
    private void processCommit(DefaultTransactionStatus status) throws TransactionException {
        try {
            boolean beforeCompletionInvoked = false;
            boolean commitListenerInvoked = false;

            try {
                boolean unexpectedRollback = false;
                prepareForCommit(status);
                triggerBeforeCommit(status);
                triggerBeforeCompletion(status);
                beforeCompletionInvoked = true;

                if (status.hasSavepoint()) {
                    if (status.isDebug()) {
                        logger.debug("Releasing transaction savepoint");
                    }
                    unexpectedRollback = status.isGlobalRollbackOnly();
                    this.transactionExecutionListeners.forEach(listener -> listener.beforeCommit(status));
                    commitListenerInvoked = true;
                    status.releaseHeldSavepoint();
                }
                else if (status.isNewTransaction()) {
                    if (status.isDebug()) {
                        logger.debug("Initiating transaction commit");
                    }
                    unexpectedRollback = status.isGlobalRollbackOnly();
                    this.transactionExecutionListeners.forEach(listener -> listener.beforeCommit(status));
                    commitListenerInvoked = true;
                    doCommit(status);
                }
                else if (isFailEarlyOnGlobalRollbackOnly()) {
                    unexpectedRollback = status.isGlobalRollbackOnly();
                }

                // Throw UnexpectedRollbackException if we have a global rollback-only
                // marker but still didn't get a corresponding exception from commit.
                if (unexpectedRollback) {
                    throw new UnexpectedRollbackException(
                            "Transaction silently rolled back because it has been marked as rollback-only");
                }
            }
            catch (UnexpectedRollbackException ex) {
                triggerAfterCompletion(status, TransactionSynchronization.STATUS_ROLLED_BACK);
                this.transactionExecutionListeners.forEach(listener -> listener.afterRollback(status, null));
                throw ex;
            }
            catch (TransactionException ex) {
                if (isRollbackOnCommitFailure()) {
                    doRollbackOnCommitException(status, ex);
                }
                else {
                    triggerAfterCompletion(status, TransactionSynchronization.STATUS_UNKNOWN);
                    if (commitListenerInvoked) {
                        this.transactionExecutionListeners.forEach(listener -> listener.afterCommit(status, ex));
                    }
                }
                throw ex;
            }
            catch (RuntimeException | Error ex) {
                if (!beforeCompletionInvoked) {
                    triggerBeforeCompletion(status);
                }
                doRollbackOnCommitException(status, ex);
                throw ex;
            }

            // Trigger afterCommit callbacks, with an exception thrown there
            // propagated to callers but the transaction still considered as committed.
            try {
                triggerAfterCommit(status);
            }
            finally {
                triggerAfterCompletion(status, TransactionSynchronization.STATUS_COMMITTED);
                if (commitListenerInvoked) {
                    this.transactionExecutionListeners.forEach(listener -> listener.afterCommit(status, null));
                }
            }

        }
        finally {
            cleanupAfterCompletion(status);
        }
    }
    @Override
    public final void rollback(TransactionStatus status) throws TransactionException {
        if (status.isCompleted()) {
            throw new IllegalTransactionStateException(
                    "Transaction is already completed - do not call commit or rollback more than once per transaction");
        }

        DefaultTransactionStatus defStatus = (DefaultTransactionStatus) status;
        processRollback(defStatus, false);
    }
    private void processRollback(DefaultTransactionStatus status, boolean unexpected) {
        try {
            boolean unexpectedRollback = unexpected;
            boolean rollbackListenerInvoked = false;

            try {
                triggerBeforeCompletion(status);

                if (status.hasSavepoint()) {
                    if (status.isDebug()) {
                        logger.debug("Rolling back transaction to savepoint");
                    }
                    this.transactionExecutionListeners.forEach(listener -> listener.beforeRollback(status));
                    rollbackListenerInvoked = true;
                    status.rollbackToHeldSavepoint();
                }
                else if (status.isNewTransaction()) {
                    if (status.isDebug()) {
                        logger.debug("Initiating transaction rollback");
                    }
                    this.transactionExecutionListeners.forEach(listener -> listener.beforeRollback(status));
                    rollbackListenerInvoked = true;
                    doRollback(status);
                }
                else {
                    // Participating in larger transaction
                    if (status.hasTransaction()) {
                        if (status.isLocalRollbackOnly() || isGlobalRollbackOnParticipationFailure()) {
                            if (status.isDebug()) {
                                logger.debug("Participating transaction failed - marking existing transaction as rollback-only");
                            }
                            doSetRollbackOnly(status);
                        }
                        else {
                            if (status.isDebug()) {
                                logger.debug("Participating transaction failed - letting transaction originator decide on rollback");
                            }
                        }
                    }
                    else {
                        logger.debug("Should roll back transaction but cannot - no transaction available");
                    }
                    // Unexpected rollback only matters here if we're asked to fail early
                    if (!isFailEarlyOnGlobalRollbackOnly()) {
                        unexpectedRollback = false;
                    }
                }
            }
            catch (RuntimeException | Error ex) {
                triggerAfterCompletion(status, TransactionSynchronization.STATUS_UNKNOWN);
                if (rollbackListenerInvoked) {
                    this.transactionExecutionListeners.forEach(listener -> listener.afterRollback(status, ex));
                }
                throw ex;
            }

            triggerAfterCompletion(status, TransactionSynchronization.STATUS_ROLLED_BACK);
            if (rollbackListenerInvoked) {
                this.transactionExecutionListeners.forEach(listener -> listener.afterRollback(status, null));
            }

            // Raise UnexpectedRollbackException if we had a global rollback-only marker
            if (unexpectedRollback) {
                throw new UnexpectedRollbackException(
                        "Transaction rolled back because it has been marked as rollback-only");
            }
        }
        finally {
            cleanupAfterCompletion(status);
        }
    }
    private void doRollbackOnCommitException(DefaultTransactionStatus status, Throwable ex) throws TransactionException {
        try {
            if (status.isNewTransaction()) {
                if (status.isDebug()) {
                    logger.debug("Initiating transaction rollback after commit exception", ex);
                }
                doRollback(status);
            }
            else if (status.hasTransaction() && isGlobalRollbackOnParticipationFailure()) {
                if (status.isDebug()) {
                    logger.debug("Marking existing transaction as rollback-only after commit exception", ex);
                }
                doSetRollbackOnly(status);
            }
        }
        catch (RuntimeException | Error rbex) {
            logger.error("Commit exception overridden by rollback exception", ex);
            triggerAfterCompletion(status, TransactionSynchronization.STATUS_UNKNOWN);
            this.transactionExecutionListeners.forEach(listener -> listener.afterRollback(status, rbex));
            throw rbex;
        }
        triggerAfterCompletion(status, TransactionSynchronization.STATUS_ROLLED_BACK);
        this.transactionExecutionListeners.forEach(listener -> listener.afterRollback(status, null));
    }

    protected final void triggerBeforeCommit(DefaultTransactionStatus status) {
        if (status.isNewSynchronization()) {
            TransactionSynchronizationUtils.triggerBeforeCommit(status.isReadOnly());
        }
    }
    protected final void triggerBeforeCompletion(DefaultTransactionStatus status) {
        if (status.isNewSynchronization()) {
            TransactionSynchronizationUtils.triggerBeforeCompletion();
        }
    }
    private void triggerAfterCommit(DefaultTransactionStatus status) {
        if (status.isNewSynchronization()) {
            TransactionSynchronizationUtils.triggerAfterCommit();
        }
    }
    private void triggerAfterCompletion(DefaultTransactionStatus status, int completionStatus) {
        if (status.isNewSynchronization()) {
            List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
            TransactionSynchronizationManager.clearSynchronization();
            if (!status.hasTransaction() || status.isNewTransaction()) {
                // No transaction or new transaction for the current scope ->
                // invoke the afterCompletion callbacks immediately
                invokeAfterCompletion(synchronizations, completionStatus);
            }
            else if (!synchronizations.isEmpty()) {
                // Existing transaction that we participate in, controlled outside
                // the scope of this Spring transaction manager -> try to register
                // an afterCompletion callback with the existing (JTA) transaction.
                registerAfterCompletionWithExistingTransaction(status.getTransaction(), synchronizations);
            }
        }
    }
    protected final void invokeAfterCompletion(List<TransactionSynchronization> synchronizations, int completionStatus) {
        TransactionSynchronizationUtils.invokeAfterCompletion(synchronizations, completionStatus);
    }
    private void cleanupAfterCompletion(DefaultTransactionStatus status) {
        status.setCompleted();
        if (status.isNewSynchronization()) {
            TransactionSynchronizationManager.clear();
        }
        if (status.isNewTransaction()) {
            doCleanupAfterCompletion(status.getTransaction());
        }
        if (status.getSuspendedResources() != null) {
            if (status.isDebug()) {
                logger.debug("Resuming suspended transaction after completion of inner transaction");
            }
            Object transaction = (status.hasTransaction() ? status.getTransaction() : null);
            resume(transaction, (SuspendedResourcesHolder) status.getSuspendedResources());
        }
    }


    //---------------------------------------------------------------------
    // Template methods to be implemented in subclasses
    //---------------------------------------------------------------------
    protected abstract Object doGetTransaction() throws TransactionException;
    protected boolean isExistingTransaction(Object transaction) throws TransactionException {
        return false;
    }    
    protected boolean useSavepointForNestedTransaction() {
        return true;
    }    
    protected abstract void doBegin(Object transaction, TransactionDefinition definition)
            throws TransactionException;    
    protected Object doSuspend(Object transaction) throws TransactionException {
        throw new TransactionSuspensionNotSupportedException(
                "Transaction manager [" + getClass().getName() + "] does not support transaction suspension");
    }    
    protected void doResume( Object transaction, Object suspendedResources) throws TransactionException {
        throw new TransactionSuspensionNotSupportedException(
                "Transaction manager [" + getClass().getName() + "] does not support transaction suspension");
    }    
    protected boolean shouldCommitOnGlobalRollbackOnly() {
        return false;
    }    
    protected void prepareForCommit(DefaultTransactionStatus status) {
    }    
    protected abstract void doCommit(DefaultTransactionStatus status) throws TransactionException;    
    protected abstract void doRollback(DefaultTransactionStatus status) throws TransactionException;    
    protected void doSetRollbackOnly(DefaultTransactionStatus status) throws TransactionException {
        throw new IllegalTransactionStateException(
                "Participating in existing transactions is not supported - when 'isExistingTransaction' " +
                        "returns true, appropriate 'doSetRollbackOnly' behavior must be provided");
    }    
    protected void registerAfterCompletionWithExistingTransaction(
            Object transaction, List<TransactionSynchronization> synchronizations) throws TransactionException {

        logger.debug("Cannot register Spring after-completion synchronization with existing transaction - " +
                "processing Spring after-completion callbacks immediately, with outcome status 'unknown'");
        invokeAfterCompletion(synchronizations, TransactionSynchronization.STATUS_UNKNOWN);
    }    
    protected void doCleanupAfterCompletion(Object transaction) {
    }


    //---------------------------------------------------------------------
    // Serialization support
    //---------------------------------------------------------------------

    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        // Rely on default serialization; just initialize state after deserialization.
        ois.defaultReadObject();

        // Initialize transient fields.
        this.logger = LogFactory.getLog(getClass());
    }
    
    protected static final class SuspendedResourcesHolder {

        private final  Object suspendedResources;

        private  List<TransactionSynchronization> suspendedSynchronizations;

        private  String name;

        private boolean readOnly;

        private  Integer isolationLevel;

        private boolean wasActive;

        private SuspendedResourcesHolder(Object suspendedResources) {
            this.suspendedResources = suspendedResources;
        }

        private SuspendedResourcesHolder(
                 Object suspendedResources, List<TransactionSynchronization> suspendedSynchronizations,
                 String name, boolean readOnly,  Integer isolationLevel, boolean wasActive) {

            this.suspendedResources = suspendedResources;
            this.suspendedSynchronizations = suspendedSynchronizations;
            this.name = name;
            this.readOnly = readOnly;
            this.isolationLevel = isolationLevel;
            this.wasActive = wasActive;
        }
    }
}
