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

import com.aoindustries.sql.wrapper.ConnectionWrapper;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.Executor;

/**
 * Wraps a {@link Connection} while tracking closed state; will only delegate methods to wrapped connection when not
 * closed.
 *
 * @author  AO Industries, Inc.
 */
// TODO: Move to own package and extend Tracker
public interface UncloseableConnectionWrapper extends ConnectionWrapper {

	/**
	 * Called when {@link #abort(java.util.concurrent.Executor)} is called and not already closed.
	 * {@link #onClose()} will never be called once aborted.  This is only called at most once.
	 *
	 * @see #abort(java.util.concurrent.Executor)
	 */
	void onAbort(Executor executor) throws SQLException;

	/**
	 * Called when {@link #close()} is called, or when the wrapped connection is discovered as closed during
	 * {@link #isClosed()}.  In either case, this is only called at most once.
	 *
	 * @see #close()
	 * @see #isClosed()
	 */
	void onClose() throws SQLException;
}
