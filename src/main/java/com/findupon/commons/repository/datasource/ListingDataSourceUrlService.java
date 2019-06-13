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

package com.findupon.commons.repository.datasource;

import com.findupon.commons.entity.datasource.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;


@Component
public class ListingDataSourceUrlService {
	private static final Logger logger = LoggerFactory.getLogger(ListingDataSourceUrlService.class);
	@Autowired private JdbcTemplate jdbcTemplate;


	public void deleteAllUrls(DataSource listingDataSource, boolean base) {
		jdbcTemplate.update("delete from listing_data_source_urls where data_source_id = ? and base = ?",
				listingDataSource.getId(), base ? 1 : 0);
	}

	public List<String> findAllNotRanUrls(DataSource listingDataSource, boolean base) {
		return jdbcTemplate.queryForList("select url from listing_data_source_urls " +
				"where data_source_id = ? and ran = 0 and base = ?", String.class, listingDataSource.getId(), base ? 1 : 0);
	}

	public void updateRan(DataSource listingDataSource, Collection<String> urls, boolean ran, boolean base) {
		if(!urls.isEmpty()) {
			String stmt = "update listing_data_source_urls set ran = " + (ran ? 1 : 0) + " where data_source_id = "
					+ listingDataSource.getId() + " and base = " + (base ? 1 : 0) + " and url in ";
			try {
				jdbcTemplate.update(urls.stream()
						.map(v -> v = "'" + v.replace("'", "\\\'") + "'")
						.collect(Collectors.joining(",", stmt + "(", ")")));
			} catch(Exception e) {
				logger.error("Error during bulk update-as-ran", e);
				throw e; // le fatal
			}
		}
	}

	public void bulkInsert(DataSource listingDataSource, Collection<String> urls, boolean base) {
		if(!urls.isEmpty()) {
			String stmt = "insert ignore into listing_data_source_urls(data_source_id, url, base) values ";
			try {
				jdbcTemplate.update(urls.stream()
						.map(v -> v = "(" + listingDataSource.getId() + ",'"
								+ v.replace("'", "\\\'") + "',"
								+ (base ? 1 : 0) + ")")
						.collect(Collectors.joining(",", stmt, ";")));
			} catch(Exception e) {
				logger.error("Error during bulk URL insert", e);
				throw e;
			}
		}
	}
}
