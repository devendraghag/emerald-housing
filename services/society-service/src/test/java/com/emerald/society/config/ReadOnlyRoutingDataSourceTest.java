package com.emerald.society.config;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class ReadOnlyRoutingDataSourceTest {
    private final DataSource writer = mock(DataSource.class);
    private final DataSource reader = mock(DataSource.class);
    private final ReadOnlyRoutingDataSource routing = new ReadOnlyRoutingDataSource();

    ReadOnlyRoutingDataSourceTest() {
        routing.setTargetDataSources(Map.of(ReadOnlyRoutingDataSource.READ_ONLY, reader));
        routing.setDefaultTargetDataSource(writer);
        routing.setLenientFallback(false);
        routing.afterPropertiesSet();
    }

    @AfterEach
    void clearTransactionState() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void readOnlyTransactionUsesReplica() throws Exception {
        Connection connection = mock(Connection.class);
        when(reader.getConnection()).thenReturn(connection);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);

        assertSame(connection, routing.getConnection());

        verify(reader).getConnection();
        verifyNoInteractions(writer);
    }

    @Test
    void regularTransactionUsesPrimary() throws Exception {
        Connection connection = mock(Connection.class);
        when(writer.getConnection()).thenReturn(connection);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);

        assertSame(connection, routing.getConnection());

        verify(writer).getConnection();
        verifyNoInteractions(reader);
    }
}
