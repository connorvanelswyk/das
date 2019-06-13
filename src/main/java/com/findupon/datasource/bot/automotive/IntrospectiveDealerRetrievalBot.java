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

package com.findupon.datasource.bot.automotive;

import com.findupon.datasource.bot.AbstractDealerRetrievalBot;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;


@Component
public class IntrospectiveDealerRetrievalBot extends AbstractDealerRetrievalBot {

	@Autowired private JdbcTemplate jdbcTemplate;


	@Override
	protected void obtainDatasourceUrls() {
		logger.info("[IntrospectiveDealerRetrievalBot] - Run initiated");

		String sql = "select distinct dealer_url from automobile where dealer_url is not null and not exists(select 1 from data_source g where dealer_url = g.url)";
		List<String> dealerUrls = jdbcTemplate.queryForList(sql, String.class);

		int totalDealerUrls = dealerUrls.size();
		logger.info("[IntrospectiveDealerRetrievalBot] - Total distinct dealer URLs [{}]", totalDealerUrls);

		int x = 0;
		for(String dealerUrl : dealerUrls) {
			if(StringUtils.isNotBlank(dealerUrl)) {
				dealerUrl = com.findupon.commons.searchparty.ScoutServices.formUrlFromString(dealerUrl, true);
				if(dealerUrl != null) {
					addDealerUrl(dealerUrl);
				}
			}
			if(++x % 150 == 0) {
				logger.info("[IntrospectiveDealerRetrievalBot] - Searched URLs [{}/{}] New potential URLs [{}]", x, totalDealerUrls, potentialUrls.size());
			}
		}
	}

	@Override
	protected String getSourceName() {
		return "Introspective";
	}

	@Override
	protected boolean verifyAssetType() {
		return true;
	}
}
