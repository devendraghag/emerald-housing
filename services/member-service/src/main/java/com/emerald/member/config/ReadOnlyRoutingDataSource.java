package com.emerald.member.config;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Routes read-only transactions to the configured replica; writes use the primary. */
public class ReadOnlyRoutingDataSource extends AbstractRoutingDataSource {
    public static final String READ_ONLY = "readOnly";

    @Override
    protected Object determineCurrentLookupKey() {
        return TransactionSynchronizationManager.isCurrentTransactionReadOnly() ? READ_ONLY : null;
    }
}
