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

package com.findupon.commons.dao;

import com.google.common.collect.Lists;
import com.findupon.commons.dao.core.EntityHelper;
import com.findupon.commons.dao.core.EntityMetaData;
import com.findupon.commons.dao.core.JdbcFacade;
import com.findupon.commons.entity.AbstractEntity;
import com.findupon.commons.utilities.SpringUtils;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.cfg.AttributeConverterDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.ResultSetExtractor;

import javax.persistence.Convert;
import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

import static com.findupon.commons.dao.core.JdbcFacade.getLong;
import static com.findupon.commons.dao.core.JdbcFacade.getObject;


public abstract class AbstractDao<A extends AbstractEntity<PK>, PK extends Number> implements EntityDao<A, PK> {
	protected final Logger logger = LoggerFactory.getLogger(getClass());

	protected @Autowired JdbcFacade jdbcFacade;
	final EntityMetaData<A, PK> entityMetaData = getEntityMetaData();


	@SuppressWarnings("unchecked")
	private <E extends A> EntityMetaData<E, PK> getEntityMetaData() {
		return EntityHelper.INSTANCE.getEntityMetaData(SpringUtils.resolveGenericTypeArg(getClass(), EntityDao.class, "A"));
	}

	@Override
	public <E extends A> void save(E entity) {
		if(entity == null) {
			return;
		}
		saveAll(Collections.singleton(entity));
	}

	@Override
	public <E extends A> void saveAll(Collection<E> entities) {
		if(entities == null || entities.isEmpty()) {
			return;
		}
		if(entities.size() > 4096) {
			for(List<E> batch : Lists.partition(new ArrayList<>(entities), 4096)) {
				saveAllInternal(batch);
			}
		} else {
			saveAllInternal(entities);
		}
	}

	private <E extends A> void saveAllInternal(Collection<E> entities) {
		StringBuilder sql = new StringBuilder("insert into " + entityMetaData.getTableName());
		StringJoiner cs = new StringJoiner(",", "(", ")");
		StringJoiner vs = new StringJoiner(",", "(", ")");
		for(String column : entityMetaData.getColumnFields().keySet()) {
			cs.add(column);
			vs.add("?");
		}
		sql.append(cs.toString());
		sql.append("values");
		sql.append(entities.stream()
				.map(a -> vs.toString())
				.collect(Collectors.joining(",")));
		sql.append("on duplicate key update ");
		sql.append(entityMetaData.getColumnFields().keySet().stream()
				.filter(c -> !"id".equals(c))
				.map(c -> c + "=values(" + c + ")")
				.collect(Collectors.joining(",")));
		sql.append(";");

		jdbcFacade.retryingUpdate(sql.toString(), ps -> {
			int index = 1;
			for(E entity : entities) {
				ensureEntityType(entity);
				for(Field f : entityMetaData.getColumnFields().values()) {
					try {
						JdbcFacade.setParam(ps, f.get(entity), index++);
					} catch(Exception e) {
						throw new RuntimeException("Error setting entity field value", e);
					}
				}
			}
		});
	}

	@Override
	public <E extends A> Optional<E> findById(PK id) {
		if(id == null) {
			return Optional.empty();
		}
		List<E> entities = findAllById(Collections.singleton(id));
		return entities.isEmpty() ? Optional.empty() : Optional.of(entities.get(0));
	}

	@Override
	public <E extends A> List<E> findAllById(Collection<PK> ids) {
		return findWhereValuesIn("id", ids);
	}

	@Override
	public long count() {
		return jdbcFacade.getCount(entityMetaData.getTableName());
	}

	@Override
	public <E extends A> void deleteAll(Collection<E> entities) {
		if(entities == null || entities.isEmpty()) {
			return;
		}
		entities.forEach(this::delete);
	}

	@Override
	public void deleteById(PK id) {
		if(id == null) {
			return;
		}
		deleteAllById(Collections.singleton(id));
	}

	@Override
	public void deleteAllById(Collection<PK> ids) {
		if(ids == null || ids.isEmpty()) {
			return;
		}
		List<PK> unique = ids.stream().distinct().collect(Collectors.toList());
		String sql = "delete from " + entityMetaData.getTableName() + " where id in";
		sql += unique.stream().map(i -> "?").collect(Collectors.joining(",", "(", ")"));
		jdbcFacade.retryingUpdate(sql, ps -> {
			for(int x = 0; x < unique.size(); x++) {
				JdbcFacade.setParam(ps, unique.get(x), x + 1);
			}
		});
	}

	<E extends A, V> List<E> findWhereValuesIn(String columnName, Collection<V> values) {
		columnName = StringUtils.trimToNull(columnName);
		if(columnName == null || values == null || values.isEmpty()) {
			return new ArrayList<>();
		}
		if(!columnName.matches("[a-zA-Z0-9_]*")) {
			logger.warn("[AbstractDao] - Invalid column name (only alphanumeric + underscores allowed). Attempted [{}]", columnName);
			return new ArrayList<>();
		}
		List<V> unique = values.stream().distinct().collect(Collectors.toList());
		String sql = "select * from " + entityMetaData.getTableName();
		sql += " where " + columnName + " in";
		sql += unique.stream().map(i -> "?").collect(Collectors.joining(",", "(", ")"));
		return jdbcFacade.query(sql, getExtractor(), ps -> {
			for(int x = 0; x < unique.size(); x++) {
				JdbcFacade.setParam(ps, unique.get(x), x + 1);
			}
		});
	}

	// /**
	//  * This is inherently dangerous, be careful.
	//  */
	// public <E extends A> List<E> customExec(String clause, char alias, PreparedStatementSetter ps) {
	// 	clause = StringUtils.trimToEmpty(clause);
	// 	if(clause.contains(";") || clause.contains("--") || clause.contains("#")) {
	// 		logger.warn("[AbstractDao] - No termination symbols allowed");
	// 		return new ArrayList<>();
	// 	}
	// 	if(!CharUtils.isAsciiAlpha(alias)) {
	// 		logger.warn("[AbstractDao] - Alias must be a letter");
	// 		return new ArrayList<>();
	// 	}
	// 	if(ps == null) {
	// 		logger.warn("[AbstractDao] - PreparedStatementSetter mustn't be null");
	// 		return new ArrayList<>();
	// 	}
	// 	String sql = "select " + alias + ".* from " + entityMetaData.getTableName() + " " + alias + " ";
	// 	return jdbcFacade.query(sql, getExtractor(), ps);
	// }

	@SuppressWarnings({"unchecked", "deprecation"})
	<E extends A> ResultSetExtractor<List<E>> getExtractor() {
		return rs -> {
			List<E> entities = new ArrayList<>();
			// ResultSetMetaData rsData = rs.getMetaData();

			while(rs.next()) {
				E entity;
				try {
					entity = (E)entityMetaData.getClazz().getDeclaredConstructor().newInstance();
				} catch(Exception e) {
					throw new RuntimeException(e);
				}
				for(Map.Entry<String, Field> entry : entityMetaData.getColumnFields().entrySet()) {
					String column = entry.getKey();
					Field field = entry.getValue();
					Object o;

					// TODO: enumerated, joins

					if(field.isAnnotationPresent(Convert.class)) {
						Convert convert = field.getAnnotation(Convert.class);
						AttributeConverterDefinition definition = AttributeConverterDefinition.from(convert.converter());
						o = definition.getAttributeConverter().convertToEntityAttribute(rs.getObject(column));
					} else if(Date.class.equals(field.getType())) {
						Timestamp timestamp = rs.getTimestamp(column);
						Date date = null;
						if(timestamp != null) {
							date = new Date(timestamp.getTime());
						}
						o = date;
					} else if(Number.class.equals(field.getType())) {
						// pk, yay autoboxing
						o = getLong(rs, column);
					} else {
						o = getObject(rs, column, field.getType());
					}
					try {
						field.set(entity, o);
					} catch(IllegalAccessException e) {
						throw new RuntimeException(e);
					}
				}
				entities.add(entity);
			}
			return entities;
		};
	}

	private <E extends A> void ensureEntityType(E entity) {
		if(!entityMetaData.getClazz().equals(entity.getClass())) {
			throw new RuntimeException(String.format("Mixed type entity list! Initial: [%s] List item: [%s]",
					entityMetaData.getClazz().getSimpleName(), entity.getClass().getSimpleName()));
		}
	}
}
