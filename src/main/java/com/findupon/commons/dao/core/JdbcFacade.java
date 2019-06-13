/*
 * Copyright 2015-2019 Connor Van Elswyk
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

package com.findupon.commons.dao.core;

import com.google.common.base.Stopwatch;
import com.findupon.commons.entity.AbstractEntity;
import com.findupon.commons.entity.building.State;
import com.findupon.commons.entity.product.ProductCondition;
import com.findupon.commons.entity.product.attribute.Attribute;
import com.findupon.commons.utilities.TimeUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.support.SQLErrorCodeSQLExceptionTranslator;
import org.springframework.jdbc.support.SQLExceptionTranslator;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;


@Component
public class JdbcFacade {
	private static final Logger logger = LoggerFactory.getLogger(JdbcFacade.class);
	private static final int maxRetryAttempts = 2;
	private static final long retryWaitMinMillis = 0x4000L;
	private static final long retryWaitMaxMillis = 0x8000L;

	private final JdbcTemplate jdbcTemplate;
	private final SQLExceptionTranslator fallbackTranslator = new SQLErrorCodeSQLExceptionTranslator() {
		/**
		 * Returning null will force the fallback translator, logging (debug level) any error code which we want seen.
		 * See {@link com.mysql.cj.exceptions.MysqlErrorNumbers} for the full list.
		 */
		@Override
		protected DataAccessException customTranslate(String task, String sql, SQLException e) {
			return null;
		}
	};

	@Autowired
	public JdbcFacade(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
		this.jdbcTemplate.setExceptionTranslator(fallbackTranslator);
	}

	public <E extends AbstractEntity<PK>, PK extends Number> List<E> query(String sql, ResultSetExtractor<List<E>> extractor, PreparedStatementSetter setter) {
		if(invalidSql(sql)) {
			return new ArrayList<>();
		}
		return jdbcTemplate.query(sql, setter, extractor);
	}

	public long getCount(String table) {
		Objects.requireNonNull(table);
		Long result = jdbcTemplate.queryForObject("select count(1) from " + table + ";", Long.class);
		return result == null ? 0 : result;
	}

	public long getDistinctCount(String table, String distinction) {
		Objects.requireNonNull(table);
		Objects.requireNonNull(distinction);
		Long result = jdbcTemplate.queryForObject("select count(distinct " + distinction + ") from " + table + ";", Long.class);
		return result == null ? 0 : result;
	}

	public int retryingUpdate(String sql, PreparedStatementSetter setter) {
		int rowCount = 0;
		if(invalidSql(sql)) {
			return rowCount;
		}
		boolean success;
		for(int x = 0; x < maxRetryAttempts; x++) {
			try {
				Stopwatch stopwatch = Stopwatch.createStarted();
				rowCount = jdbcTemplate.update(sql, setter);
				if(rowCount > 0) {
					logger.debug("[JdbcFacade] - Update operation on complete in [{}] seconds with row count [{}]", TimeUtils.formatSeconds(stopwatch), rowCount);
				} else {
					logger.warn("[JdbcFacade] - Zero row count for update operation. SQL [{}]", sql);
				}
				success = true;
			} catch(DataAccessException e) {
				success = false;
				if(x + 1 == maxRetryAttempts) {
					logger.error("[JdbcFacade] - Final error during insert/ update operation", e);
					return rowCount;
				} else {
					logger.warn("[JdbcFacade] - Attempt [{}] error during insert/ update operation", x + 1, e);
				}
			}
			if(success) {
				return rowCount;
			} else {
				try {
					Thread.sleep(ThreadLocalRandom.current().nextLong(retryWaitMinMillis, retryWaitMaxMillis));
				} catch(InterruptedException e) {
					logger.warn("[JdbcFacade] - Thread interrupt during retry wait, aborting current operation");
					return rowCount;
				}
			}
		}
		return rowCount;
	}

	/**
	 * Prevent basic invalid or unsupported (drop, etc.) and mass updates or deletes by enforcing parameters and relative
	 * clauses for each operation.
	 */
	private boolean invalidSql(String sql) {
		if(StringUtils.isBlank(sql)) {
			logger.error("[JdbcFacade] - Missing SQL required for update operation");
			return true;
		}
		sql = sql.toLowerCase();
		if(!sql.contains("?")) {
			logger.error("[JdbcFacade] - SQL must be parameterized with '?'. Attempted: [{}]", sql);
			return true;
		}
		if(sql.contains("insert into ")) {
			if(!sql.contains("values") && !sql.contains("select")) {
				logger.error("[JdbcFacade] - Insert operation must contain 'values' or 'select'. Attempted: [{}]", sql);
				return true;
			}
			return false;
		}
		if(sql.contains("update ")) {
			if(!sql.contains("set ")) {
				logger.error("[JdbcFacade] - Update operation must contain 'set'. Attempted: [{}]", sql);
				return true;
			}
			if(!sql.contains("where ")) {
				logger.error("[JdbcFacade] - Update operation must contain a 'where' clause. Attempted: [{}]", sql);
				return true;
			}
			return false;
		}
		if(sql.contains("delete from ")) {
			if(!sql.contains("where ")) {
				logger.error("[JdbcFacade] - Delete operation must contain a 'where' clause. Attempted: [{}]", sql);
				return true;
			}
			return false;
		}
		if(sql.contains("select") && sql.contains("from ")) {
			if(!sql.contains("where ")) {
				logger.error("[JdbcFacade] - Select operation must contain a 'where' clause. Attempted: [{}]", sql);
				return true;
			}
			return false;
		}
		logger.error("[JdbcFacade] - SQL must be an insert, update, or delete operation. Attempted: [{}]", sql);
		return true;
	}

	public static <T> T getObject(ResultSet rs, String columnLabel, Class<T> type) throws SQLException {
		T o = rs.getObject(columnLabel, type);
		if(rs.wasNull()) {
			return null;
		}
		return o;
	}

	public static Integer getInteger(ResultSet rs, String columnLabel) throws SQLException {
		int i = rs.getInt(columnLabel);
		if(rs.wasNull()) {
			return null;
		}
		return i;
	}

	public static Long getLong(ResultSet rs, String columnLabel) throws SQLException {
		long l = rs.getLong(columnLabel);
		if(rs.wasNull()) {
			return null;
		}
		return l;
	}

	public static Double getDouble(ResultSet rs, String columnLabel) throws SQLException {
		double d = rs.getDouble(columnLabel);
		if(rs.wasNull()) {
			return null;
		}
		return d;
	}

	public static Float getFloat(ResultSet rs, String columnLabel) throws SQLException {
		float f = rs.getFloat(columnLabel);
		if(rs.wasNull()) {
			return null;
		}
		return f;
	}

	public static Byte getByte(ResultSet rs, String columnLabel) throws SQLException {
		byte b = rs.getByte(columnLabel);
		if(rs.wasNull()) {
			return null;
		}
		return b;
	}

	public static void setParam(PreparedStatement ps, Object arg, int index) throws SQLException {
		if(arg instanceof Date) {
			ps.setTimestamp(index, new Timestamp(((Date)arg).getTime()));
		} else if(arg instanceof Integer) {
			ps.setInt(index, (Integer)arg);
		} else if(arg instanceof Long) {
			ps.setLong(index, (Long)arg);
		} else if(arg instanceof Double) {
			ps.setDouble(index, (Double)arg);
		} else if(arg instanceof Float) {
			ps.setFloat(index, (Float)arg);
		} else if(arg instanceof BigDecimal) {
			ps.setBigDecimal(index, (BigDecimal)arg);
		} else if(arg instanceof Boolean) {
			ps.setBoolean(index, (Boolean)arg);
		} else if(arg instanceof Attribute.GenericMatching) {
			ps.setInt(index, ((Attribute.GenericMatching)arg).getId());
		} else if(arg instanceof State) {
			ps.setString(index, ((State)arg).getAbbreviation());
		} else if(arg instanceof ProductCondition) {
			ps.setInt(index, ((ProductCondition)arg).getId());
		} else {
			ps.setString(index, (String)arg);
		}
	}
}
