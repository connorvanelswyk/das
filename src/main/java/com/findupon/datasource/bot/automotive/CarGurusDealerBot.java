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
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class CarGurusDealerBot extends AbstractDealerRetrievalBot {
	private final String rootUrl = "https://www.cargurus.com/";
	private final List<String> dealerPageUrls = new ArrayList<>();


	@Override
	protected void obtainDatasourceUrls() {
		rules = com.plainviewrd.commons.netops.ConnectionAgent.INSTANCE.downloadRobotRules(rootUrl, com.plainviewrd.commons.netops.entity.ProxyMode.ROTATE_LOCATION, com.plainviewrd.commons.netops.entity.AgentMode.ROTATE).getLeft();
		if(rules.isDeferVisits() || rules.isAllowNone()) {
			logger.error("[{}] - Robots.txt allow none or defer visits [{}]", getSourceName(), rootUrl);
			return;
		}
		if(rules.getCrawlDelay() > baseCrawlRateMillis) {
			baseCrawlRateMillis = rules.getCrawlDelay();
		}
		for(String sitemapPath : new String[]{"sitemap8.xml.gz", "sitemap9.xml.gz"}) {
			addDealerPages(rootUrl + sitemapPath);
		}
		Set<String> distinctDealers = new HashSet<>(dealerPageUrls);
		dealerPageUrls.clear();
		dealerPageUrls.addAll(distinctDealers);
		logger.info("[{}] - Final dealer page size [{}]", getSourceName(), String.format("%,d", dealerPageUrls.size()));

		for(int x = 0; x < dealerPageUrls.size(); x++) {
			if(x % 25 == 0) {
				logger.info(com.plainviewrd.commons.utilities.ConsoleColors.green("[{}] - Completed [{}]"), getSourceName(), String.format("%.2f%%", x / (float)dealerPageUrls.size() * 100));
			}
			String dealerPageUrl = dealerPageUrls.get(x);

			Document dealerDoc = connectAndDownload(dealerPageUrl, rules, baseCrawlRateMillis);
			if(dealerDoc == null) {
				logger.warn("[{}] - Dealer page document came back null [{}]", getSourceName(), dealerPageUrl);
				continue;
			}
			Element dealerHref = dealerDoc.selectFirst("p[class=cg-listingDetail-dealerInfo-links] > a");
			if(dealerHref == null || !dealerHref.hasText()) {
				logger.warn("[{}] - No dealer page link element or text [{}]", getSourceName(), dealerPageUrl);
				continue;
			}
			addDealerUrl(com.plainviewrd.commons.searchparty.ScoutServices.formUrlFromString(dealerHref.ownText(), true));
		}
	}

	private void addDealerPages(String sitemapUrl) {
		Document sitemap = com.plainviewrd.commons.netops.ConnectionAgent.INSTANCE.xmlDownload(sitemapUrl, com.plainviewrd.commons.netops.entity.ProxyMode.ROTATE_LOCATION, com.plainviewrd.commons.netops.entity.AgentMode.ROTATE);
		if(sitemap == null) {
			logger.error("[{}] - Site map document [{}] came back null!", getSourceName(), sitemapUrl);
			return;
		}
		sitemap.select("loc").stream()
				.distinct()
				.map(Element::text)
				.map(s -> com.plainviewrd.commons.searchparty.ScoutServices.encodeSpacing(s, true))
				.filter(s -> StringUtils.containsIgnoreCase(s, "/Cars/m-"))
				.forEach(dealerPageUrls::add);
	}

	@Override
	protected String getSourceName() {
		return "CarGurusDealerBot";
	}

	@Override
	protected boolean verifyAssetType() {
		return false;
	}
}
