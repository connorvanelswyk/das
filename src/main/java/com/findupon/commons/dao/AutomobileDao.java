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
import com.findupon.commons.entity.product.automotive.Automobile;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;


@Component
public class AutomobileDao extends ProductDao<Automobile> {

	@Override
	public <E extends Automobile> Optional<E> find(E automobile) {
		if(automobile == null) {
			return Optional.empty();
		}
		if(automobile.getId() != null || automobile.getDataSourceId() != null && automobile.getListingId() != null) {
			return super.find(automobile);
		}
		if(automobile.getVin() == null || automobile.getDataSourceId() == null) {
			logger.warn("[AutomobileDao] - Find requires (ID) || (listing ID && data source ID) || (VIN && data source ID)");
			return Optional.empty();
		}
		String sql = "select * from " + entityMetaData.getTableName() + " where vin = ? and data_source_id = ?;";
		List<E> automobiles = jdbcFacade.query(sql, getExtractor(), ps -> {
			JdbcFacade.setParam(ps, automobile.getVin(), 1);
			JdbcFacade.setParam(ps, automobile.getDataSourceId(), 2);
		});
		if(automobiles.isEmpty()) {
			return Optional.empty();
		}
		if(automobiles.size() > 1) {
			logger.warn("[AutomobileDao] - More than one automobile found! {}", automobile);
		}
		return Optional.of(automobiles.get(0));
	}

	public List<Automobile> findByVin(String vin) {
		return super.findByAggregationValue(vin);
	}

	public List<Automobile> findByVins(Collection<String> vins) {
		return super.findByAggregationValues(vins);
	}
}
