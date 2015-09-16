/*
 * Copyright (C) 2014 Dell, Inc.
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

package com.dell.doradus.service.db.cql;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.BatchStatement;
import com.datastax.driver.core.BatchStatement.Type;
import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSetFuture;
import com.dell.doradus.core.ServerConfig;
import com.dell.doradus.service.db.ColumnDelete;
import com.dell.doradus.service.db.ColumnUpdate;
import com.dell.doradus.service.db.DBTransaction;
import com.dell.doradus.service.db.DColumn;
import com.dell.doradus.service.db.RowDelete;
import com.dell.doradus.service.db.Tenant;
import com.dell.doradus.service.db.cql.CQLStatementCache.Update;

/**
 * Concrete DBTransaction for use with the Cassandra CQL API. Updates are stored in
 * separate maps for column adds/updates and row/column deletions.
 */
public class CQLTransaction extends DBTransaction {
    protected final Logger m_logger = LoggerFactory.getLogger(getClass().getSimpleName());

    public CQLTransaction(Tenant tenant) {
        super(CQLService.storeToCQLName(tenant.getKeyspace()));
    }
    
    /**
     * Apply the updates accumulated in this transaction. The updates are cleared even if
     * the update fails.
     */
    public void commit() {
        try {
            applyUpdates();
        } catch (Exception e) {
            m_logger.error("Updates failed", e);
            throw e;
        } finally {
            clear();
        }
    }
    
    // Execute a batch statement that applies all updates in this transaction.
    private void applyUpdates() {
        if (getMutationsCount() == 0) {
            m_logger.debug("Skipping commit with no updates");
        } else if (ServerConfig.getInstance().async_updates) {
            executeUpdatesAsynchronous();
        } else {
            executeUpdatesSynchronous();
        }
    }

     // Execute all updates asynchronously and wait for results.
    private void executeUpdatesAsynchronous() {
        Collection<BoundStatement> mutations = getMutations();
        List<ResultSetFuture> futureList = new ArrayList<>(mutations.size());
        for(BoundStatement mutation: mutations) {
            ResultSetFuture future = CQLService.instance().getSession().executeAsync(mutation);
            futureList.add(future);
        }
        m_logger.debug("Waiting for {} asynchronous futures", futureList.size());
        for (ResultSetFuture future : futureList) {
            future.getUninterruptibly();
        }
    }

    // Execute all updates and deletes using synchronous statements.
    private void executeUpdatesSynchronous() {
        BatchStatement batchState = new BatchStatement(Type.UNLOGGED);
        batchState.addAll(getMutations());
        executeBatch(batchState);
    }

    private List<BoundStatement> getMutations() {
        List<BoundStatement> mutations = new ArrayList<>();
        for(ColumnUpdate value: getColumnUpdates()) {
            boolean isBinaryValue = CQLService.instance().columnValueIsBinary(getNamespace(), value.getStoreName());
            BoundStatement bstmt = addColumnUpdate(value.getStoreName(), value.getRowKey(), value.getColumn(), isBinaryValue);
            mutations.add(bstmt);
        }
        for(ColumnDelete value: getColumnDeletes()) {
            BoundStatement bstmt = addColumnDelete(value.getStoreName(), value.getRowKey(), value.getColumnName());
            mutations.add(bstmt);
        }
        for(RowDelete value: getRowDeletes()) {
            BoundStatement bstmt = addRowDelete(value.getStoreName(), value.getRowKey());
            mutations.add(bstmt);
        }
        return mutations;
    }
    
    // Create and return a BoundStatement for the given column update.
    private BoundStatement addColumnUpdate(String tableName, String key, DColumn column, boolean isBinaryValue) {
        PreparedStatement prepState =
                CQLService.instance().getPreparedUpdate(getNamespace(), Update.INSERT_ROW, tableName);
        BoundStatement boundState = prepState.bind();
        boundState.setString(0, key);
        boundState.setString(1, column.getName());
        if (isBinaryValue) {
            boundState.setBytes(2, ByteBuffer.wrap(column.getRawValue()));
        } else {
            boundState.setString(2, column.getValue());
        }
        return boundState;
    }
    
    
    // Create and return a BoundStatement that deletes the given column.
    private BoundStatement addColumnDelete(String tableName, String key, String colName) {
        PreparedStatement prepState =
                CQLService.instance().getPreparedUpdate(getNamespace(), Update.DELETE_COLUMN, tableName);
        BoundStatement boundState = prepState.bind();
        boundState.setString(0, key);
        boundState.setString(1, colName);
        return boundState;
    }
    
    // Create and return a BoundStatement that deletes the given row.
    private BoundStatement addRowDelete(String tableName, String key) {
        PreparedStatement prepState =
                CQLService.instance().getPreparedUpdate(getNamespace(), Update.DELETE_ROW, tableName);
        BoundStatement boundState = prepState.bind();
        boundState.setString(0, key);
        return boundState;
    }
    
    
    // Execute and given update statement.
    private void executeBatch(BatchStatement batchState) {
        if (batchState.size() > 0) {
            m_logger.debug("Executing synchronous batch with {} statements", batchState.size());
            CQLService.instance().getSession().execute(batchState);
        }
    }
    
}