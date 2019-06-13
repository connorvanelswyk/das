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

import com.plainviewrd.datasource.bot.AbstractDealerRetrievalBot;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static com.plainviewrd.commons.utilities.ConsoleColors.green;
import static com.plainviewrd.commons.utilities.JsoupUtils.firstChild;


@Component
public class EdmundsDealerBot extends AbstractDealerRetrievalBot {
	private final String BASE_URL = "https://www.edmunds.com/dealerships/";
	private final String[] SITES_TO_AVOID = {"carmax", "autonation", "carfax", "autotrader"};
	private final Set<String> visitedDealerHrefs = new HashSet<>();

	@Autowired private JdbcTemplate jdbcTemplate;


	@Override
	public void obtainDatasourceUrls() {
		List<String> retrieveByPageUrls = jdbcTemplate.queryForList("select url from edmunds_dealer_urls where ran = 0", String.class);
		if(CollectionUtils.isEmpty(retrieveByPageUrls)) {
			retrieveByPageUrls = new ArrayList<>();

			logger.info("[EdmundsDealerBot] - No urls found in database, starting indexing process");

			Document document = com.plainviewrd.commons.netops.ConnectionAgent.INSTANCE.stealthDownload(BASE_URL).getDocument();
			if(document == null) {
				logger.warn("[EdmundsDealerBot] - Error retrieving base URL [{}]", BASE_URL);
				return;
			}
			Element byStateDiv = firstChild(document.select("div[id=lcl_state]"));
			if(byStateDiv != null) {
				List<String> byStateHrefs = byStateDiv.getElementsByTag("a").stream()
						.map(e -> e.attr("abs:href"))
						.collect(Collectors.toList());

				for(String byStateHref : byStateHrefs) {
					Document stateDocument = com.plainviewrd.commons.netops.ConnectionAgent.INSTANCE.stealthDownload(byStateHref).getDocument();
					if(stateDocument == null) {
						logger.warn("[EdmundsDealerBot] - Error retrieving by state URL [{}]", BASE_URL);
						continue;
					}
					Element byCityDiv = firstChild(stateDocument.select("div[id=lcl_state_city] > div"));
					if(byCityDiv != null) {
						byCityDiv.getElementsByTag("a").stream().map(e -> e.attr("abs:href")).forEach(retrieveByPageUrls::add);
					}
				}
			}
			Set<String> urls = new HashSet<>(retrieveByPageUrls);
			retrieveByPageUrls.clear();
			retrieveByPageUrls.addAll(urls);
			jdbcTemplate.update("insert into edmunds_dealer_urls (url) values " + retrieveByPageUrls.stream()
					.map(s -> "('" + s + "')")
					.collect(Collectors.joining(",")));
		}
		int completed = 0;
		logger.info(green("[EdmundsDealerBot] - All by page URLs indexed. Size [{}]"), retrieveByPageUrls.size());
		for(String byPageUrl : retrieveByPageUrls) {
			retrieveByPage(byPageUrl);
			completed++;
			jdbcTemplate.update("update edmunds_dealer_urls set ran = 1 where url = ?", byPageUrl);
			logger.info(green("[EdmundsDealerBot] - Completed [{}/{}] by page URLs"), completed, retrieveByPageUrls.size());
		}
		jdbcTemplate.update("delete from edmunds_dealer_urls");
		logger.info(green("[EdmundsDealerBot] - Process completed"));
	}

	@Override
	protected String getSourceName() {
		return "Edmunds";
	}

	@Override
	protected boolean verifyAssetType() {
		return false;
	}

	private void retrieveByPage(String url) {
		Document document;
		do {
			document = com.plainviewrd.commons.netops.ConnectionAgent.INSTANCE.stealthDownload(url).getDocument();
			if(document == null) {
				logger.warn("[EdmundsDealerBot] - Error retrieving by-page URL [{}]", BASE_URL);
				break;
			}
			List<String> dealerHrefs = document.select("a[class=fn url org]").stream()
					.map(e -> e.attr("abs:href"))
					.filter(s -> Arrays.stream(SITES_TO_AVOID).noneMatch(a -> StringUtils.containsIgnoreCase(s, a)))
					.collect(Collectors.toList());

			for(String dealerHref : dealerHrefs) {
				if(!visitedDealerHrefs.add(dealerHref.toLowerCase())) {
					continue;
				}
				if(!dataSourceRepo.searchByUrl(url).isEmpty()) {
					logger.warn("[EdmundsDealerBot] - Already existing pre-connection DS URL [{}]", url);
					continue;
				}
				Document dealerPage = com.plainviewrd.commons.netops.ConnectionAgent.INSTANCE.stealthDownload(dealerHref).getDocument();
				if(dealerPage == null) {
					logger.warn("[EdmundsDealerBot] - Error retrieving dealer page URL [{}]", BASE_URL);
					continue;
				}
				Element dealerAnchor = firstChild(dealerPage.select("a[class=dealer-details-web-site-link medium]"));
				if(dealerAnchor != null) {
					String href = dealerAnchor.attr("href");
					if(StringUtils.isNotEmpty(href) && Arrays.stream(SITES_TO_AVOID).noneMatch(a -> StringUtils.containsIgnoreCase(href, a))) {
						addDealerUrl(href.toLowerCase());
					}
				}
			}
			Element nextPageButton = firstChild(document.select("span[class=listingsNextPage]"));
			if(nextPageButton == null) {
				return;
			}
			Element nextAnchor = firstChild(nextPageButton.getElementsByTag("a"));
			if(nextAnchor == null) {
				return;
			}
			url = nextAnchor.attr("abs:href");
		} while(document.html().contains("listingsNextPage"));
	}
}
