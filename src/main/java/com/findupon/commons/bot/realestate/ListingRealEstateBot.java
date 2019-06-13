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

package com.findupon.commons.bot.realestate;

import com.findupon.commons.bot.ListingBot;
import com.findupon.commons.building.PriceOperations;
import com.findupon.commons.dao.ProductDao;
import com.findupon.commons.entity.product.attribute.RealEstateType;
import com.findupon.commons.entity.product.realestate.RealEstate;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;


@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public abstract class ListingRealEstateBot extends ListingBot<RealEstate> {

	@Autowired protected JdbcTemplate jdbcTemplate;

	Set<String> baseUrlsByZip(Function<String, String> zipQueryMapper) {
		String baseUrl = getDataSource().getUrl();
		jdbcTemplate.query("select distinct zip from zipcode_data", rs -> {
			String zip = rs.getString("zip");
			if(zip != null) {
				String query = zipQueryMapper.apply(zip);
				if(query != null) {
					if(query.startsWith("/")) {
						query = query.substring(1);
					}
					baseUrls.add(baseUrl + query);
				}
			}
		});
		return baseUrls;
	}

	void buildByPage(String baseUrl, List<String> noMatchingResultsMessages,
	                 Function<Document, List<RealEstate>> serpBuilder,
	                 BiPredicate<Document, Integer> areMoreResultsAvailable) {

		if(!baseUrl.contains("/1_p/")) {
			logger.error(logPre() + "Base URL [{}] does not contain required page specifier [{}]", baseUrl, "/1_p/");
			return;
		}
		Document firstPage = tryTwice(baseUrl);
		if(firstPage == null) {
			return;
		}
		Document currentPage;
		int pageNumber = 0;
		if(noMatchingResults(noMatchingResultsMessages, firstPage)) {
			return;
		}
		do {
			pageNumber++;
			if(firstPage != null) {
				currentPage = firstPage;
				firstPage = null;
			} else {
				String pageUrl = StringUtils.replace(baseUrl, "/1_p/", "/" + pageNumber + "_p/");
				currentPage = tryTwice(pageUrl);
			}
			if(sleep()) {
				return;
			}
			if(currentPage == null) {
				logger.warn(logPre() + "Null document returned at page [{}], returning. Serp URL: [{}]", pageNumber, baseUrl);
				return;
			}
			logger.debug(logPre() + "Building from serp at [{}]", currentPage.location());
			if(noMatchingResults(noMatchingResultsMessages, currentPage)) {
				return;
			}
			List<RealEstate> built = serpBuilder.apply(currentPage);
			if(built.isEmpty()) {
				logger.debug(logPre() + "No products returned by builder at page [{}], returning. URL: [{}]", pageNumber, currentPage.location());
				return;
			}
			built.forEach(this::validateSetMetaAndAdd);

			if(products.size() >= ProductDao.productWriteThreshold) {
				logger.debug(logPre() + "Persisting [{}] real estates to the db (over threshold)", products.size());
				persistAndClear();
			}
		} while(areMoreResultsAvailable.test(currentPage, pageNumber));

		logger.debug(logPre() + "Persisting [{}] real estates to the db (final)", products.size());
		persistAndClear();
	}

	private boolean noMatchingResults(List<String> messages, Document document) {
		for(String message : messages) {
			if(StringUtils.containsIgnoreCase(document.html(), message)) {
				logger.debug(logPre() + "No matching results message [{}] found at [{}]", message, document.location());
				return true;
			}
		}
		return false;
	}

	void setPrice(RealEstate realEstate, BigDecimal price) {
		if(realEstate == null || price == null || realEstate.getProductTerm() == null) {
			return;
		}
		switch(realEstate.getProductTerm()) {
			case RENT:
				if(PriceOperations.inRange(price, 100, 40_000)) {
					realEstate.setPrice(price);
				}
				break;
			case OWN:
			case NFS:
				int min = 3000;
				int max = 300_000_000;
				if(RealEstateType.LAND.equals(realEstate.getRealEstateType())) {
					min = 500;
				}
				if(PriceOperations.inRange(price, min, max)) {
					realEstate.setPrice(price);
				}
				break;
		}
	}
}
