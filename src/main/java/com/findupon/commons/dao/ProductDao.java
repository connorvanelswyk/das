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

import com.findupon.commons.dao.core.JdbcFacade;
import com.findupon.commons.entity.product.AggregationColumn;
import com.findupon.commons.entity.product.Product;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Field;
import java.util.*;


public abstract class ProductDao<A extends Product> extends AbstractDao<A, Long> {

	private final String aggregationColumnName = getAggregationColumn();
	public static final int productWriteThreshold = 64;


	@Override
	public <E extends A> Optional<E> find(E entity) {
		if(entity == null) {
			return Optional.empty();
		}
		if(entity.getId() != null) {
			return findById(entity.getId());
		}
		List<E> entities = new ArrayList<>();
		if(entity.getDataSourceId() != null && entity.getListingId() != null) {
			entities = findByDataSourceIdAndListingId(entity.getDataSourceId(), entity.getListingId());
		}
		if(entities.isEmpty()) {
			return Optional.empty();
		}
		if(entities.size() > 1) {
			logger.warn("[ProductDao] - More than one entity found! {}", entity);
		}
		return Optional.of(entities.get(0));
	}

	@Override
	public <E extends A> void delete(E entity) {
		if(entity == null) {
			return;
		}
		if(entity.getId() != null) {
			deleteById(entity.getId());
			return;
		}
		if(entity.getDataSourceId() == null || StringUtils.isBlank(entity.getListingId())) {
			logger.error("[ProductDao] - Deletion by entity without ID requires a data source ID and listing ID. [{}]", entity);
			return;
		}
		String sql = "delete from " + entityMetaData.getTableName() + " where data_source_id = ? and listing_id = ?";
		jdbcFacade.retryingUpdate(sql, ps -> {
			JdbcFacade.setParam(ps, entity.getDataSourceId(), 1);
			JdbcFacade.setParam(ps, entity.getListingId(), 2);
		});
	}

	public <E extends A, V> List<E> findByAggregationValue(V aggregationValue) {
		return findByAggregationValues(Collections.singleton(aggregationValue));
	}

	public <E extends A, V> List<E> findByAggregationValues(Collection<V> aggregationValues) {
		return findWhereValuesIn(aggregationColumnName, aggregationValues);
	}

	public long getDistinctAggregationValueCount() {
		return jdbcFacade.getDistinctCount(entityMetaData.getTableName(), aggregationColumnName);
	}

	public <E extends A> List<E> findByDataSourceIdAndListingId(Long dataSourceId, String listingId) {
		if(dataSourceId == null || StringUtils.isBlank(listingId)) {
			logger.error("[ProductDao] - Find by entity without ID requires a data source ID and listing ID");
			return new ArrayList<>();
		}
		String sql = "select * from " + entityMetaData.getTableName() + " where data_source_id = ? and listing_id = ?";
		return jdbcFacade.query(sql, getExtractor(), ps -> {
			JdbcFacade.setParam(ps, dataSourceId, 1);
			JdbcFacade.setParam(ps, listingId, 2);
		});
	}

	public <E extends A> List<E> findAllByDataSourceId(Long dataSourceId) {
		if(dataSourceId == null) {
			return new ArrayList<>();
		}
		String sql = "select * from " + entityMetaData.getTableName() + " where data_source_id = ?;";
		return jdbcFacade.query(sql, getExtractor(), ps -> JdbcFacade.setParam(ps, dataSourceId, 1));
	}

	public long countByDataSourceId(Long dataSourceId) {
		if(dataSourceId == null) {
			return 0;
		}
		return jdbcFacade.getCount(entityMetaData.getTableName() + " where data_source_id = " + dataSourceId + ";");
	}

	private String getAggregationColumn() {
		for(Map.Entry<String, Field> entry : entityMetaData.getColumnFields().entrySet()) {
			if(entry.getValue().isAnnotationPresent(AggregationColumn.class)) {
				return entry.getKey();
			}
		}
		return null;
	}
}
