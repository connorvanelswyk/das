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

import com.findupon.commons.entity.datasource.StagingDataSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
public interface StagingDataSourceRepo extends JpaRepository<StagingDataSource, Integer> {

	@Query(value = "select * " +
			"from staging_data_source " +
			"where id in ( " +
			"    select id " +
			"    from ( " +
			"             select " +
			"                 id, " +
			"                 substring_index( " +
			"                     reverse( " +
			"                         substr( " +
			"                             reverse(no_protocol_ds_url), 1 + locate('/', reverse(no_protocol_ds_url)) " +
			"                         ) " +
			"                     ), 'www.', -1 " +
			"                 ) stripped_ds_url, " +
			"                 substring_index( " +
			"                     reverse( " +
			"                         substr( " +
			"                             reverse(no_protocol_search_url), 1 + locate('/', reverse(no_protocol_search_url)) " +
			"                         ) " +
			"                     ), 'www.', -1 " +
			"                 ) stripped_search_url " +
			"             from ( " +
			"                      select " +
			"                          id, " +
			"                          lower( " +
			"                              substring_index( " +
			"                                  (case when right(url, 1) = '/' " +
			"                                      then url " +
			"                                   else concat(url, '/') end), " +
			"                                  '://', -1 " +
			"                              ) " +
			"                          ) no_protocol_ds_url, " +
			"                          lower( " +
			"                              substring_index( " +
			"                                  (case when right(:url, 1) = '/' " +
			"                                      then :url " +
			"                                   else concat(:url, '/') end), " +
			"                                  '://', -1 " +
			"                              ) " +
			"                          ) no_protocol_search_url " +
			"                      from staging_data_source " +
			"                  ) protocol_stripped " +
			"         ) prefix_stripped " +
			"    where binary stripped_ds_url = binary stripped_search_url " +
			")", nativeQuery = true)
	List<StagingDataSource> searchByUrl(@Param("url") String url);
}
