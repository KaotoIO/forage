package io.kaoto.forage.core.jta;

import jakarta.transaction.HeuristicMixedException;
import jakarta.transaction.HeuristicRollbackException;
import jakarta.transaction.RollbackException;
import jakarta.transaction.Status;
import jakarta.transaction.SystemException;
import jakarta.transaction.Transaction;

import org.apache.camel.jta.JtaTransactionPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class TransactionalJtaTransactionPolicy extends JtaTransactionPolicy {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionalJtaTransactionPolicy.class);

    private final jakarta.transaction.TransactionManager transactionManager =
            com.arjuna.ats.jta.TransactionManager.transactionManager();

    protected void runWithTransaction(final Runnable runnable, final boolean isNew) throws Throwable {
        if (isNew) {
            begin();
        }
        try {
            runnable.run();
        } catch (Throwable e) {
            rollback(isNew);
            throw e;
        }
        if (isNew) {
            commit();
        }
    }

    private void begin() throws Exception {
        transactionManager.begin();
    }

    private void commit() throws Exception {
        try {
            transactionManager.commit();
        } catch (HeuristicMixedException | HeuristicRollbackException | RollbackException | SystemException e) {
            throw new RuntimeException("Unable to commit transaction", e);
        } catch (Exception | Error e) {
            rollback(true);
            throw e;
        }
    }

    protected final void rollback(boolean isNew) {
        try {
            if (isNew) {
                transactionManager.rollback();
            } else {
                transactionManager.setRollbackOnly();
            }
        } catch (Throwable e) {
            LOG.warn("Could not rollback transaction!", e);
        }
    }

    protected final Transaction suspendTransaction() throws Exception {
        return transactionManager.suspend();
    }

    protected final void resumeTransaction(final Transaction suspendedTransaction) {
        if (suspendedTransaction == null) {
            return;
        }

        try {
            transactionManager.resume(suspendedTransaction);
        } catch (Throwable e) {
            LOG.warn("Could not resume transaction!", e);
        }
    }

    protected final boolean hasActiveTransaction() throws Exception {
        return transactionManager.getStatus() != Status.STATUS_MARKED_ROLLBACK
                && transactionManager.getStatus() != Status.STATUS_NO_TRANSACTION;
    }
}
