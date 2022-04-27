/*
 * ao-sql-pool - Legacy AO JDBC connection pool.
 * Copyright (C) 2021, 2022  AO Industries, Inc.
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
module com.aoapps.sql.pool {
  exports com.aoapps.sql.pool;
  // Direct
  requires com.aoapps.hodgepodge; // <groupId>com.aoapps</groupId><artifactId>ao-hodgepodge</artifactId>
  requires com.aoapps.lang; // <groupId>com.aoapps</groupId><artifactId>ao-lang</artifactId>
  requires com.aoapps.sql; // <groupId>com.aoapps</groupId><artifactId>ao-sql</artifactId>
  requires com.aoapps.sql.tracker; // <groupId>com.aoapps</groupId><artifactId>ao-sql-tracker</artifactId>
  requires com.aoapps.sql.wrapper; // <groupId>com.aoapps</groupId><artifactId>ao-sql-wrapper</artifactId>
  // Java SE
  requires java.sql;
} // TODO: Avoiding rewrite-maven-plugin-4.22.2 truncation
