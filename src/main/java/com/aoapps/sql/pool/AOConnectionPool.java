/*
 * ao-sql-pool - Legacy AO JDBC connection pool.
 * Copyright (C) 2000, 2001, 2002, 2003, 2004, 2005, 2006, 2007, 2008, 2009, 2010, 2011, 2013, 2015, 2016, 2018, 2019, 2020, 2021  AO Industries, Inc.
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
 * along with ao-sql-pool.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.aoapps.sql.pool;

import com.aoapps.hodgepodge.io.AOPool;
import com.aoapps.lang.AutoCloseables;
import com.aoapps.lang.Throwables;
import com.aoapps.sql.Connections;
import com.aoapps.sql.tracker.ConnectionTracker;
import com.aoapps.sql.tracker.ConnectionTrackerImpl;
import com.aoapps.sql.tracker.DatabaseMetaDataTrackerImpl;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reusable connection pooling with dynamic flaming tiger feature.
 *
 * @author  AO Industries, Inc.
 */
// TODO: Use Connection.isValid while allocating from pool and/or putting back into pool?
// TODO: Or use isValid in background connection management?
// TODO: Or, when a connection is checked back in with an unresolved fail-fast state (not current used by pool), then
//       background validate all connections?
//
// TODO: Warn in AOConnectionPool when max connections are higher than database supports (check once on connect, check
//       occasionally?  PostgreSQL JDBC driver seems to have hard-coded value of 8192 currently, so this might not be
//       very meaningful.
//
// TODO: Implement DataSource and ConnectionPoolDataSource, if want to further develop this, but we should probably
//       kill this in favor of one of several other pooling options.  Deprecate and just use commons-dbcp.  This pooling
//       code predates dbcp-1.0 by around three years, but there is probably no benefit to maintaining this separate
//       pooling implementation.
public class AOConnectionPool extends AOPool<Connection, SQLException, SQLException> {

	/**
	 * The read-only state of connections while idle in the pool.
	 */
	public static final boolean IDLE_READ_ONLY = true;

	private final String driver;
	private final String url;
	private final String user;
	private final String password;

	public AOConnectionPool(String driver, String url, String user, String password, int numConnections, long maxConnectionAge, Logger logger) {
		super(AOConnectionPool.class.getName()+"?url=" + url+"&user="+user, numConnections, maxConnectionAge, logger);
		this.driver = driver;
		this.url = url;
		this.user = user;
		this.password = password;
	}

	@SuppressWarnings("null")
	private Connection unwrap(Connection conn) throws SQLException {
		IPooledConnection wrapper;
		if(conn instanceof IPooledConnection) {
			wrapper = (IPooledConnection)conn;
		} else {
			wrapper = conn.unwrap(IPooledConnection.class);
		}
		if(wrapper.getPool() == this) {
			return wrapper.getWrapped();
		} else {
			throw new SQLException("Connection from a different pool, cannot unwrap");
		}
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * If the connection not already {@linkplain Connection#isClosed() closed}, and is not
	 * {@linkplain Connection#getAutoCommit() auto-commit}, the connection will be
	 * {@linkplain Connection#rollback() rolled back} and set back to auto-commit before closing.
	 * </p>
	 */
	@Override
	@SuppressWarnings({"UseSpecificCatch", "TooBroadCatch"})
	protected void close(Connection conn) throws SQLException {
		Throwable t0 = null;
		// Unwrap
		try {
			conn = unwrap(conn);
		} catch(Throwable t) {
			t0 = Throwables.addSuppressed(t0, t);
		}
		// Close wrapped (or parameter "conn" when can't unwrap)
		try {
			if(!conn.isClosed() && !conn.getAutoCommit()) {
				conn.rollback();
				conn.setAutoCommit(true);
			}
		} catch(Throwable t) {
			t0 = Throwables.addSuppressed(t0, t);
		}
		AutoCloseables.closeAndThrow(t0, SQLException.class, SQLException::new, conn);
	}

	/**
	 * Gets a read/write connection to the database with a transaction level of
	 * {@link Connections#DEFAULT_TRANSACTION_ISOLATION},
	 * warning when a connection is already used by this thread.
	 * <p>
	 * The connection will be in auto-commit mode, as configured by {@link #resetConnection(java.sql.Connection)}
	 * </p>
	 * <p>
	 * If all the connections in the pool are busy and the pool is at capacity, waits until a connection becomes
	 * available.
	 * </p>
	 * <p>
	 * The connection will be a {@link ConnectionTracker}, which may be unwrapped via {@link Connection#unwrap(java.lang.Class)}.
	 * The connection tracking is used to close/free all objects before returning the connection to the pool.
	 * </p>
	 *
	 * @return  The read/write connection to the database
	 *
	 * @throws  SQLException  when an error occurs, or when a thread attempts to allocate more than half the pool
	 *
	 * @see  #getConnection(int, boolean, int)
	 * @see  Connection#close()
	 */
	// Note: Matches AOPool.getConnection()
	// Note:      Is AOConnectionPool.getConnection()
	// Note: Matches Database.getConnection()
	// Note: Matches DatabaseConnection.getConnection()
	@Override
	public Connection getConnection() throws SQLException {
		return getConnection(Connections.DEFAULT_TRANSACTION_ISOLATION, false, 1);
	}

	/**
	 * Gets a read/write connection to the database with a transaction level of
	 * {@link Connections#DEFAULT_TRANSACTION_ISOLATION}.
	 * <p>
	 * The connection will be in auto-commit mode, as configured by {@link #resetConnection(java.sql.Connection)}
	 * </p>
	 * <p>
	 * If all the connections in the pool are busy and the pool is at capacity, waits until a connection becomes
	 * available.
	 * </p>
	 * <p>
	 * The connection will be a {@link ConnectionTracker}, which may be unwrapped via {@link Connection#unwrap(java.lang.Class)}.
	 * The connection tracking is used to close/free all objects before returning the connection to the pool.
	 * </p>
	 *
	 * @param  maxConnections  The maximum number of connections expected to be used by the current thread.
	 *                         This should normally be one to avoid potential deadlock.
	 *                         <p>
	 *                         The connection will continue to be considered used by the allocating thread until
	 *                         released (via {@link Connection#close()}, even if the connection is shared by another
	 *                         thread.
	 *                         </p>
	 *
	 * @return  The read/write connection to the database
	 *
	 * @throws  SQLException  when an error occurs, or when a thread attempts to allocate more than half the pool
	 *
	 * @see  #getConnection(int, boolean, int)
	 * @see  Connection#close()
	 */
	// Note: Matches AOPool.getConnection(int)
	// Note:      Is AOConnectionPool.getConnection(int)
	// Note: Matches Database.getConnection(int)
	// Note: Matches DatabaseConnection.getConnection(int)
	@Override
	public Connection getConnection(int maxConnections) throws SQLException {
		return getConnection(Connections.DEFAULT_TRANSACTION_ISOLATION, false, maxConnections);
	}

	/**
	 * Gets a connection to the database with a transaction level of
	 * {@link Connections#DEFAULT_TRANSACTION_ISOLATION},
	 * warning when a connection is already used by this thread.
	 * <p>
	 * The connection will be in auto-commit mode, as configured by {@link #resetConnection(java.sql.Connection)}
	 * </p>
	 * <p>
	 * If all the connections in the pool are busy and the pool is at capacity, waits until a connection becomes
	 * available.
	 * </p>
	 * <p>
	 * The connection will be a {@link ConnectionTracker}, which may be unwrapped via {@link Connection#unwrap(java.lang.Class)}.
	 * The connection tracking is used to close/free all objects before returning the connection to the pool.
	 * </p>
	 *
	 * @param  readOnly  The {@link Connection#setReadOnly(boolean) read-only flag}
	 *
	 * @return  The connection to the database
	 *
	 * @throws  SQLException  when an error occurs, or when a thread attempts to allocate more than half the pool
	 *
	 * @see  #getConnection(int, boolean, int)
	 * @see  Connection#close()
	 */
	// Note:      Is AOConnectionPool.getConnection(boolean)
	// Note: Matches Database.getConnection(boolean)
	// Note: Matches DatabaseConnection.getConnection(boolean)
	public Connection getConnection(boolean readOnly) throws SQLException {
		return getConnection(Connections.DEFAULT_TRANSACTION_ISOLATION, readOnly, 1);
	}

	/**
	 * Gets a connection to the database,
	 * warning when a connection is already used by this thread.
	 * <p>
	 * The connection will be in auto-commit mode, as configured by {@link #resetConnection(java.sql.Connection)}
	 * </p>
	 * <p>
	 * If all the connections in the pool are busy and the pool is at capacity, waits until a connection becomes
	 * available.
	 * </p>
	 * <p>
	 * The connection will be a {@link ConnectionTracker}, which may be unwrapped via {@link Connection#unwrap(java.lang.Class)}.
	 * The connection tracking is used to close/free all objects before returning the connection to the pool.
	 * </p>
	 *
	 * @param  isolationLevel  The {@link Connection#setTransactionIsolation(int) transaction isolation level}
	 *
	 * @param  readOnly        The {@link Connection#setReadOnly(boolean) read-only flag}
	 *
	 * @return  The connection to the database
	 *
	 * @throws  SQLException  when an error occurs, or when a thread attempts to allocate more than half the pool
	 *
	 * @see  #getConnection(int, boolean, int)
	 * @see  Connection#close()
	 */
	// Note:      Is AOConnectionPool.getConnection(int, boolean)
	// Note: Matches Database.getConnection(int, boolean)
	// Note: Matches DatabaseConnection.getConnection(int, boolean)
	public Connection getConnection(int isolationLevel, boolean readOnly) throws SQLException {
		return getConnection(isolationLevel, readOnly, 1);
	}

	/**
	 * Gets a connection to the database.
	 * <p>
	 * The connection will be in auto-commit mode, as configured by {@link #resetConnection(java.sql.Connection)}
	 * </p>
	 * <p>
	 * If all the connections in the pool are busy and the pool is at capacity, waits until a connection becomes
	 * available.
	 * </p>
	 * <p>
	 * The connection will be a {@link ConnectionTracker}, which may be unwrapped via {@link Connection#unwrap(java.lang.Class)}.
	 * The connection tracking is used to close/free all objects before returning the connection to the pool.
	 * </p>
	 *
	 * @param  isolationLevel  The {@link Connection#setTransactionIsolation(int) transaction isolation level}
	 *
	 * @param  readOnly        The {@link Connection#setReadOnly(boolean) read-only flag}
	 *
	 * @param  maxConnections  The maximum number of connections expected to be used by the current thread.
	 *                         This should normally be one to avoid potential deadlock.
	 *                         <p>
	 *                         The connection will continue to be considered used by the allocating thread until
	 *                         released (via {@link Connection#close()}, even if the connection is shared by another
	 *                         thread.
	 *                         </p>
	 *
	 * @return  The connection to the database
	 *
	 * @throws  SQLException  when an error occurs, or when a thread attempts to allocate more than half the pool
	 *
	 * @see  Connection#close()
	 */
	// Note:      Is AOConnectionPool.getConnection(int, boolean, int)
	// Note: Matches Database.getConnection(int, boolean, int)
	// Note: Matches DatabaseConnection.getConnection(int, boolean, int)
	@SuppressWarnings({"UseSpecificCatch", "AssignmentToCatchBlockParameter"})
	public Connection getConnection(int isolationLevel, boolean readOnly, int maxConnections) throws SQLException {
		Connection conn = null;
		try {
			conn = super.getConnection(maxConnections);
			assert conn.getAutoCommit();
			assert conn.isReadOnly() == IDLE_READ_ONLY : "Connection not reset";
			assert conn.getTransactionIsolation() == Connections.DEFAULT_TRANSACTION_ISOLATION : "Connection not reset";
			if(readOnly != IDLE_READ_ONLY) conn.setReadOnly(readOnly);
			if(isolationLevel != Connections.DEFAULT_TRANSACTION_ISOLATION) conn.setTransactionIsolation(isolationLevel);
			return conn;
		} catch(Throwable t0) {
			try {
				release(conn);
			} catch(Throwable t) {
				t0 = Throwables.addSuppressed(t0, t);
			}
			throw Throwables.wrap(t0, SQLException.class, SQLException::new);
		}
	}

	private static final ConcurrentMap<String, Object> driversLoaded = new ConcurrentHashMap<>();

	/**
	 * Loads a driver at most once.
	 */
	private static void loadDriver(String classname) throws ClassNotFoundException {
		if(!driversLoaded.containsKey(classname)) {
			Class<?> driver = Class.forName(classname);
			driversLoaded.putIfAbsent(classname, driver);
		}
	}

	private static class PooledDatabaseMetaData extends DatabaseMetaDataTrackerImpl {

		private PooledDatabaseMetaData(PooledConnection pooledConnection, DatabaseMetaData wrapped) {
			super(pooledConnection, wrapped);
		}

		@Override
		protected PooledConnection getConnectionWrapper() {
			return (PooledConnection)super.getConnectionWrapper();
		}

		@Override
		public int getMaxConnections() throws SQLException {
			AOConnectionPool pool = getConnectionWrapper().pool;
			int maxConnections = super.getMaxConnections();
			int poolSize = pool.getPoolSize();
			if(maxConnections == 0) {
				// Unknown, just return pool size
				return poolSize;
			} else {
				if(poolSize > maxConnections) {
					// Warn and constrain by maxConnections
					if(pool.logger.isLoggable(Level.WARNING)) {
						pool.logger.warning(
							"AOConnectionPool.poolSize > DatabaseMetaData.maxConnections: "
							+ poolSize + " > " + maxConnections
						);
					}
					return maxConnections;
				} else {
					// Constrain by pool size
					return poolSize;
				}
			}
		}
	}

	private static interface IPooledConnection extends ConnectionTracker {
		AOConnectionPool getPool();
	}

	private static class PooledConnection extends ConnectionTrackerImpl implements IPooledConnection {

		private final AOConnectionPool pool;

		private PooledConnection(AOConnectionPool pool, Connection wrapped) {
			super(wrapped);
			this.pool = pool;
		}

		@Override
		public AOConnectionPool getPool() {
			return pool;
		}

		@Override
		protected PooledDatabaseMetaData newDatabaseMetaDataWrapper(DatabaseMetaData metaData) {
			return new PooledDatabaseMetaData(this, metaData);
		}

		/**
		 * Releases to the pool instead of closing the connection.
		 */
		@Override
		protected void doClose() throws SQLException {
			pool.release(this);
		}

		/**
		 * Aborts the connection then releases to the pool.
		 */
		@Override
		@SuppressWarnings({"UseSpecificCatch", "TooBroadCatch"})
		protected void doAbort(Executor executor) throws SQLException {
			Throwable t0 = null;
			try {
				super.doAbort(executor);
			} catch(Throwable t) {
				t0 = Throwables.addSuppressed(t0, t);
			}
			try {
				pool.release(this);
			} catch(Throwable t) {
				t0 = Throwables.addSuppressed(t0, t);
			}
			if(t0 != null) {
				throw Throwables.wrap(t0, SQLException.class, SQLException::new);
			}
		}
	}

	@Override
	@SuppressWarnings({"UseSpecificCatch", "TooBroadCatch"})
	protected Connection getConnectionObject() throws SQLException {
		try {
			if(Thread.currentThread().isInterrupted()) throw new SQLException("Thread interrupted");
			loadDriver(driver);
			Connection conn = DriverManager.getConnection(url, user, password);
			boolean successful = false;
			try {
				if(Thread.currentThread().isInterrupted()) throw new SQLException("Thread interrupted"); // TODO: Make an InterruptedSQLException, with a static checkInterrupted() method?
				if(conn.getClass().getName().startsWith("org.postgresql.")) {
					// getTransactionIsolation causes a round-trip to the database, this wrapper caches the value and avoids unnecessary sets
					// to eliminate unnecessary round-trips and improve performance over high-latency links.
					conn = new PostgresqlConnectionWrapper(conn);
				}
				PooledConnection pooledConnection = new PooledConnection(this, conn);
				successful = true;
				return pooledConnection;
			} finally {
				if(!successful) conn.close();
			}
		} catch(ThreadDeath td) {
			throw td;
		} catch(Throwable t) {
			logger.logp(Level.SEVERE, AOConnectionPool.class.getName(), "getConnectionObject", "url="+url+"&user="+user+"&password=XXXXXXXX", t);
			throw Throwables.wrap(t, SQLException.class, SQLException::new);
		}
	}

	@Override
	protected boolean isClosed(Connection conn) throws SQLException {
		return unwrap(conn).isClosed();
	}

	@SuppressWarnings("deprecation")
	@Override
	protected void printConnectionStats(Appendable out, boolean isXhtml) throws IOException {
		out.append("  <thead>\n"
				+ "    <tr><th colspan=\"2\"><span style=\"font-size:large\">JDBC Driver</span></th></tr>\n"
				+ "  </thead>\n");
		super.printConnectionStats(out, isXhtml);
		out.append("    <tr><td>Driver:</td><td>");
		com.aoapps.hodgepodge.util.EncodingUtils.encodeHtml(driver, false, false, out, isXhtml);
		out.append("</td></tr>\n"
				+ "    <tr><td>URL:</td><td>");
		com.aoapps.hodgepodge.util.EncodingUtils.encodeHtml(url, false, false, out, isXhtml);
		out.append("</td></tr>\n"
				+ "    <tr><td>User:</td><td>");
		com.aoapps.hodgepodge.util.EncodingUtils.encodeHtml(user, false, false, out, isXhtml);
		out.append("</td></tr>\n"
				+ "    <tr><td>Password:</td><td>");
		int len=password.length();
		for(int c=0;c<len;c++) {
			out.append('*');
		}
		out.append("</td></tr>\n");
	}

	/**
	 * Default implementation of {@link #logConnection(java.sql.Connection)}
	 *
	 * @see  #logConnection(java.sql.Connection)
	 */
	public static void defaultLogConnection(Connection conn, Logger logger) throws SQLException {
		SQLWarning warning = conn.getWarnings();
		if(warning != null) logger.log(Level.WARNING, null, warning);
	}

	/**
	 * @see  #defaultLogConnection(java.sql.Connection, java.util.logging.Logger)
	 */
	@Override
	protected void logConnection(Connection conn) throws SQLException {
		defaultLogConnection(conn, logger);
	}

	/**
	 * Default implementation of {@link #resetConnection(java.sql.Connection)}
	 * <ol>
	 * <li>{@linkplain Connection#clearWarnings() Warnings are cleared}</li>
	 * <li>Any {@linkplain Connection#getAutoCommit() transaction in-progress} is {@linkplain Connection#rollback() rolled-back}</li>
	 * <li>Auto-commit is enabled</li>
	 * <li>Read-only state is set to {@link #IDLE_READ_ONLY}</li>
	 * <li>Transaction isolation level set to {@link Connections#DEFAULT_TRANSACTION_ISOLATION}</li>
	 * </ol>
	 *
	 * @see  #resetConnection(java.sql.Connection)
	 */
	public static void defaultResetConnection(Connection conn) throws SQLException {
		if(Thread.currentThread().isInterrupted()) throw new SQLException("Thread interrupted");
		conn.clearWarnings();

		// Autocommit will always be turned on, regardless what a previous transaction might have done
		if(!conn.getAutoCommit()) {
			if(Thread.currentThread().isInterrupted()) throw new SQLException("Thread interrupted");
			conn.rollback();
			conn.setAutoCommit(true);
		}
		// Restore the connection to the idle read-only state
		if(conn.isReadOnly() != IDLE_READ_ONLY) {
			if(Thread.currentThread().isInterrupted()) throw new SQLException("Thread interrupted");
			conn.setReadOnly(IDLE_READ_ONLY);
		}
		// Restore to default transaction level
		if(conn.getTransactionIsolation() != Connections.DEFAULT_TRANSACTION_ISOLATION) {
			if(Thread.currentThread().isInterrupted()) throw new SQLException("Thread interrupted"); // TODO: Should we do these types of interrupted checks more?
			conn.setTransactionIsolation(Connections.DEFAULT_TRANSACTION_ISOLATION);
		}
	}

	/**
	 * @see  #defaultResetConnection(java.sql.Connection)
	 */
	@Override
	protected void resetConnection(Connection conn) throws SQLException {
		defaultResetConnection(conn);
	}

	@Override
	protected SQLException newException(String message, Throwable cause) {
		if(cause instanceof SQLException) return (SQLException)cause;
		if(message == null) {
			if(cause == null) {
				return new SQLException();
			} else {
				return new SQLException(cause);
			}
		} else {
			if(cause == null) {
				return new SQLException(message);
			} else {
				return new SQLException(message, cause);
			}
		}
	}

	@Override
	protected SQLException newInterruptedException(String message, Throwable cause) {
		// Restore the interrupted status
		Thread.currentThread().interrupt();
		return newException(message, cause);
	}

	@Override
	public String toString() {
		return "AOConnectionPool(url=\"" + url + "\", user=\"" + user + "\")";
	}
}
