/*
 * ao-sql-pool - Legacy AO JDBC connection pool.
 * Copyright (C) 2020  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of ao-sql-pool.
 *
 * ao-sql-pool is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ao-sql-pool is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with ao-sql-pool.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.aoindustries.sql.pool;

import com.aoindustries.sql.wrapper.ArrayWrapperImpl;
import com.aoindustries.sql.wrapper.BlobWrapperImpl;
import com.aoindustries.sql.wrapper.CallableStatementWrapperImpl;
import com.aoindustries.sql.wrapper.ClobWrapperImpl;
import com.aoindustries.sql.wrapper.ConnectionWrapperImpl;
import com.aoindustries.sql.wrapper.DatabaseMetaDataWrapperImpl;
import com.aoindustries.sql.wrapper.DriverWrapper;
import com.aoindustries.sql.wrapper.NClobWrapperImpl;
import com.aoindustries.sql.wrapper.PreparedStatementWrapperImpl;
import com.aoindustries.sql.wrapper.SQLXMLWrapperImpl;
import com.aoindustries.sql.wrapper.SavepointWrapperImpl;
import com.aoindustries.sql.wrapper.StatementWrapperImpl;
import com.aoindustries.sql.wrapper.StructWrapperImpl;
import java.sql.Connection;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Savepoint;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Wraps a {@link Connection} while tracking closed state; will only delegate methods to wrapped connection when not
 * closed.
 *
 * @author  AO Industries, Inc.
 */
// TODO: Move to own package and extend Tracker
public class UncloseableConnectionWrapperImpl extends ConnectionWrapperImpl implements UncloseableConnectionWrapper {

	private final AtomicBoolean closed = new AtomicBoolean();

	public UncloseableConnectionWrapperImpl(DriverWrapper driverWrapper, Connection wrapped) {
		super(driverWrapper, wrapped);
	}

	public UncloseableConnectionWrapperImpl(Connection wrapped) {
		super(wrapped);
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * This default implementation calls {@link Connection#abort(java.util.concurrent.Executor)}
	 * on the wrapped connection.
	 * </p>
	 *
	 * @see  #getWrappedConnection()
	 * @see  Connection#abort(java.util.concurrent.Executor)
	 */
	@Override
	public void onAbort(Executor executor) throws SQLException {
		getWrapped().abort(executor);
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * This default implementation does nothing.
	 * </p>
	 *
	 * @see  #getWrappedConnection()
	 * @see  Connection#close()
	 */
	@Override
	public void onClose() throws SQLException {
		// Do nothing
	}

	private <X extends Throwable> void checkNotClosed(Function<String,X> throwableSupplier) throws X {
		if(closed.get()) throw throwableSupplier.apply("Connection closed");
	}

	private void checkNotClosed() throws SQLException {
		checkNotClosed(message -> new SQLException(message));
	}

	@Override
	public StatementWrapperImpl createStatement() throws SQLException {
		checkNotClosed();
		return super.createStatement();
	}

	@Override
	public PreparedStatementWrapperImpl prepareStatement(String sql) throws SQLException {
		checkNotClosed();
		return super.prepareStatement(sql);
	}

	@Override
	public CallableStatementWrapperImpl prepareCall(String sql) throws SQLException {
		checkNotClosed();
		return super.prepareCall(sql);
	}

	@Override
	public String nativeSQL(String sql) throws SQLException {
		checkNotClosed();
		return super.nativeSQL(sql);
	}

	@Override
	public void setAutoCommit(boolean autoCommit) throws SQLException {
		checkNotClosed();
		super.setAutoCommit(autoCommit);
	}

	@Override
	public boolean getAutoCommit() throws SQLException {
		checkNotClosed();
		return super.getAutoCommit();
	}

	@Override
	public void commit() throws SQLException {
		checkNotClosed();
		super.commit();
	}

	@Override
	public void rollback() throws SQLException {
		checkNotClosed();
		super.rollback();
	}

	/**
	 * Blocks direct call to wrapped {@link Connection#close()}, instead setting closed flag and dispatching to
	 * {@link #onClose()}.  Will only call {@link #onClose()} once.
	 *
	 * @see #onClose()
	 */
	@Override
	public void close() throws SQLException {
		if(!closed.getAndSet(true)) {
			onClose();
		}
	}

	/**
	 * When already known to be closed, returns {@code true} without calling wrapped {@link Connection#isClosed()}.
	 * When a connection is discovered to be closed, calls {@link #onClose()}.  Will only call {@link #onClose()} once.
	 */
	@Override
	public boolean isClosed() throws SQLException {
		if(closed.get()) return true;
		boolean wrappedClosed = super.isClosed();
		if(wrappedClosed) {
			// Connection detected as closed, call onClose() now
			if(!closed.getAndSet(true)) {
				onClose();
			}
		}
		return wrappedClosed;
	}

	@Override
	public DatabaseMetaDataWrapperImpl getMetaData() throws SQLException {
		checkNotClosed();
		return super.getMetaData();
	}

	@Override
	public void setReadOnly(boolean readOnly) throws SQLException {
		checkNotClosed();
		super.setReadOnly(readOnly);
	}

	@Override
	public boolean isReadOnly() throws SQLException {
		checkNotClosed();
		return super.isReadOnly();
	}

	@Override
	public void setCatalog(String catalog) throws SQLException {
		checkNotClosed();
		super.setCatalog(catalog);
	}

	@Override
	public String getCatalog() throws SQLException {
		checkNotClosed();
		return super.getCatalog();
	}

	@Override
	public void setTransactionIsolation(int level) throws SQLException {
		checkNotClosed();
		super.setTransactionIsolation(level);
	}

	@Override
	public int getTransactionIsolation() throws SQLException {
		checkNotClosed();
		return super.getTransactionIsolation();
	}

	@Override
	public SQLWarning getWarnings() throws SQLException {
		checkNotClosed();
		return super.getWarnings();
	}

	@Override
	public void clearWarnings() throws SQLException {
		checkNotClosed();
		super.clearWarnings();
	}

	@Override
	public StatementWrapperImpl createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
		checkNotClosed();
		return super.createStatement(resultSetType, resultSetConcurrency);
	}

	@Override
	public PreparedStatementWrapperImpl prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
		checkNotClosed();
		return super.prepareStatement(sql, resultSetType, resultSetConcurrency);
	}

	@Override
	public CallableStatementWrapperImpl prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
		checkNotClosed();
		return super.prepareCall(sql, resultSetType, resultSetConcurrency);
	}

	@Override
	public Map<String,Class<?>> getTypeMap() throws SQLException {
		checkNotClosed();
		return super.getTypeMap();
	}

	@Override
	public void setTypeMap(Map<String,Class<?>> map) throws SQLException {
		checkNotClosed();
		super.setTypeMap(map);
	}

	@Override
	public void setHoldability(int holdability) throws SQLException {
		checkNotClosed();
		super.setHoldability(holdability);
	}

	@Override
	public int getHoldability() throws SQLException {
		checkNotClosed();
		return super.getHoldability();
	}

	@Override
	public SavepointWrapperImpl setSavepoint() throws SQLException {
		checkNotClosed();
		return super.setSavepoint();
	}

	@Override
	public SavepointWrapperImpl setSavepoint(String name) throws SQLException {
		checkNotClosed();
		return super.setSavepoint(name);
	}

	@Override
	public void rollback(Savepoint savepoint) throws SQLException {
		checkNotClosed();
		super.rollback(savepoint);
	}

	@Override
	public void releaseSavepoint(Savepoint savepoint) throws SQLException {
		checkNotClosed();
		super.releaseSavepoint(savepoint);
	}

	@Override
	public StatementWrapperImpl createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
		checkNotClosed();
		return super.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability);
	}

	@Override
	public PreparedStatementWrapperImpl prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
		checkNotClosed();
		return super.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
	}

	@Override
	public CallableStatementWrapperImpl prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
		checkNotClosed();
		return super.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
	}

	@Override
	public PreparedStatementWrapperImpl prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
		checkNotClosed();
		return super.prepareStatement(sql, autoGeneratedKeys);
	}

	@Override
	public PreparedStatementWrapperImpl prepareStatement(String sql, int columnIndexes[]) throws SQLException {
		checkNotClosed();
		return super.prepareStatement(sql, columnIndexes);
	}

	@Override
	public PreparedStatementWrapperImpl prepareStatement(String sql, String columnNames[]) throws SQLException {
		checkNotClosed();
		return super.prepareStatement(sql, columnNames);
	}

	@Override
	public ClobWrapperImpl createClob() throws SQLException {
		checkNotClosed();
		return super.createClob();
	}

	@Override
	public BlobWrapperImpl createBlob() throws SQLException {
		checkNotClosed();
		return super.createBlob();
	}

	@Override
	public NClobWrapperImpl createNClob() throws SQLException {
		checkNotClosed();
		return super.createNClob();
	}

	@Override
	public SQLXMLWrapperImpl createSQLXML() throws SQLException {
		checkNotClosed();
		return super.createSQLXML();
	}

	@Override
	public boolean isValid(int timeout) throws SQLException {
		if(closed.get()) return false;
		return super.isValid(timeout);
	}

	@Override
	public void setClientInfo(String name, String value) throws SQLClientInfoException {
		checkNotClosed(message -> new SQLClientInfoException(message, null));
		super.setClientInfo(name, value);
	}

	@Override
	public void setClientInfo(Properties properties) throws SQLClientInfoException {
		checkNotClosed(message -> new SQLClientInfoException(message, null));
		super.setClientInfo(properties);
	}

	@Override
	public String getClientInfo(String name) throws SQLException {
		checkNotClosed();
		return super.getClientInfo(name);
	}

	@Override
	public Properties getClientInfo() throws SQLException {
		checkNotClosed();
		return super.getClientInfo();
	}

	@Override
	public ArrayWrapperImpl createArrayOf(String typeName, Object[] elements) throws SQLException {
		checkNotClosed();
		return super.createArrayOf(typeName, elements);
	}

	@Override
	public StructWrapperImpl createStruct(String typeName, Object[] attributes) throws SQLException {
		checkNotClosed();
		return super.createStruct(typeName, attributes);
	}

	@Override
	public int getNetworkTimeout() throws SQLException {
		checkNotClosed();
		return super.getNetworkTimeout();
	}

	@Override
	public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
		checkNotClosed();
		super.setNetworkTimeout(executor, milliseconds);
	}

	@Override
	public void setSchema(String schema) throws SQLException {
		checkNotClosed();
		super.setSchema(schema);
	}

	@Override
	public String getSchema() throws SQLException {
		checkNotClosed();
		return super.getSchema();
	}

	/**
	 * Blocks direct call to wrapped {@link Connection#abort(java.util.concurrent.Executor)}, instead setting closed
	 * flag and dispatching to {@link #onAbort(java.util.concurrent.Executor)}.  Will only call
	 * {@link #onAbort(java.util.concurrent.Executor)} once.
	 *
	 * @see #onAbort(java.util.concurrent.Executor)
	 */
	@Override
	public void abort(Executor executor) throws SQLException {
		if(!closed.getAndSet(true)) {
			onAbort(executor);
		}
	}
}
