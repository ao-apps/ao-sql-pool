/*
 * ao-sql-pool - Legacy AO JDBC connection pool.
 * Copyright (C) 2008, 2009, 2010, 2011, 2013, 2016, 2019, 2020  AO Industries, Inc.
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

import com.aoindustries.sql.wrapper.ConnectionWrapperImpl;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Wraps a {@link Connection} and caches the transaction level, intended to avoid unnecessary round-trips imposed by
 * PostgreSQL {@link Connection#getTransactionIsolation()} and {@link Connection#setTransactionIsolation(int)}.
 *
 * @author  AO Industries, Inc.
 */
// TODO: Rename to something not specific to PostgreSQL?
// TODO: Cache many other methods, too?
public class PostgresqlConnectionWrapper extends ConnectionWrapperImpl {

	private final Object transactionIsolationLevelLock = new Object();
	private int transactionIsolationLevel;

	PostgresqlConnectionWrapper(Connection conn) throws SQLException {
		super(conn);
		this.transactionIsolationLevel = conn.getTransactionIsolation();
	}

	@Override
	public void setTransactionIsolation(int level) throws SQLException {
		Connection wrapped = getWrapped();
		synchronized(transactionIsolationLevelLock) {
			if(
				level != this.transactionIsolationLevel
				// Also call wrapped connection when isClosed to get the error from the wrapped driver.
				|| wrapped.isClosed()
			) {
				wrapped.setTransactionIsolation(level);
				this.transactionIsolationLevel = level;
			}
		}
	}

	@Override
	public int getTransactionIsolation() throws SQLException {
		Connection wrapped = getWrapped();
		synchronized(transactionIsolationLevelLock) {
			// Also call wrapped connection when isClosed to get the error from the wrapped driver.
			if(wrapped.isClosed()) {
				transactionIsolationLevel = wrapped.getTransactionIsolation();
			}
			return transactionIsolationLevel;
		}
	}
}
