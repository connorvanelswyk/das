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
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;


public class DealerRaterBot extends AbstractDealerRetrievalBot {
	private final String rootUrl = "https://www.dealerrater.com/reviews/";


	@Override
	protected void obtainDatasourceUrls() {
		Document landingPage = com.plainviewrd.commons.netops.ConnectionAgent.INSTANCE.stealthDownload(rootUrl).getDocument();
		if(landingPage == null) {
			logger.error("[{}] - Null document returned by root url! [{}]", getSourceName(), rootUrl);
			return;
		}
		rules = com.plainviewrd.commons.netops.ConnectionAgent.INSTANCE.downloadRobotRules(rootUrl, com.plainviewrd.commons.netops.entity.ProxyMode.ROTATE_LOCATION, com.plainviewrd.commons.netops.entity.AgentMode.ROTATE).getLeft();
		if(rules.isDeferVisits() || rules.isAllowNone()) {
			logger.error("[{}] - Robots.txt allow none or defer visits [{}]", getSourceName(), rootUrl);
			return;
		}
		if(rules.getCrawlDelay() > baseCrawlRateMillis) {
			baseCrawlRateMillis = rules.getCrawlDelay();
		}

		Set<String> byStateLinks = landingPage.select("a[class=dark-grey]").stream()
				.map(e -> e.attr("abs:href"))
				.filter(StringUtils::isNotEmpty)
				.filter(s -> attributeMatcher.getFullAttributeMap().entrySet().stream()
						.flatMap(e -> e.getValue().getPermutations().stream())
						.anyMatch(m -> StringUtils.containsIgnoreCase(s, m)))
				.distinct()
				.collect(Collectors.toSet());
		logger.info(com.plainviewrd.commons.utilities.ConsoleColors.green("[{}] - By state indexing complete, [{}] states added"), getSourceName(), byStateLinks.size());

		Set<String> serpLinks = new HashSet<>();
		for(String stateLink : byStateLinks) {
			Document statePage = connectAndDownload(stateLink, rules, baseCrawlRateMillis);
			if(statePage != null) {
				statePage.select("a[class=\"dark-grey notranslate\"]").stream()
						.map(e -> e.attr("abs:href"))
						.filter(StringUtils::isNotEmpty)
						.distinct()
						.forEach(serpLinks::add);
			}
		}
		logger.info(com.plainviewrd.commons.utilities.ConsoleColors.green("[{}] - Serp indexing complete, [{}] serp pages added"), getSourceName(), serpLinks.size());

		Set<String> detailPageLinks = new HashSet<>();
		for(String serpLink : serpLinks) {
			Document serpPage;
			Set<String> links = new HashSet<>();
			int pageNum = 1;
			do {
				String url = serpLink + "?page=" + pageNum++;
				serpPage = connectAndDownload(url, rules, baseCrawlRateMillis);
				if(serpPage != null) {
					links = serpPage.select("a[class=\"teal boldest font-20 uppercase dealer-name-link\"]").stream()
							.map(e -> e.attr("abs:href"))
							.filter(StringUtils::isNotEmpty)
							.distinct()
							.collect(Collectors.toSet());
					detailPageLinks.addAll(links);
				}
			}
			while(serpPage != null
					&& !links.isEmpty()
					&& !StringUtils.containsIgnoreCase(serpPage.html(), "0 Results")
					&& com.plainviewrd.commons.utilities.JsoupUtils.firstChild(serpPage.select("div[class=\"page_inactive next page\"]")) == null);
		}
		logger.info(com.plainviewrd.commons.utilities.ConsoleColors.green("[{}] - Detail page indexing complete, [{}] detail pages added"), getSourceName(), detailPageLinks.size());

		for(String detailPageLink : detailPageLinks) {
			Document detailPage = connectAndDownload(detailPageLink, rules, baseCrawlRateMillis);
			if(detailPage != null) {
				detailPage.select("a[onclick=\"viewDealerWebsiteEvent();\"]").stream()
						.map(e -> e.attr("abs:href"))
						.filter(StringUtils::isNotEmpty)
						.filter(s -> !StringUtils.containsIgnoreCase(s, "dealerrater"))
						.distinct()
						.forEach(this::addDealerUrl);
			}
		}
	}

	@Override
	protected String getSourceName() {
		return "DealerRater";
	}

	@Override
	protected boolean verifyAssetType() {
		return true;
	}
}
