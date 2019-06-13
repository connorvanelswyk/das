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

package com.findupon.datasource.bot;

import com.google.common.base.Stopwatch;
import com.findupon.commons.entity.datasource.AssetType;
import com.findupon.commons.entity.datasource.DataSource;
import com.findupon.commons.utilities.*;
import crawlercommons.robots.BaseRobotRules;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.UrlValidator;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.findupon.commons.utilities.ConsoleColors.red;


@Component
public abstract class AbstractDealerRetrievalBot {
	protected final Logger logger = LoggerFactory.getLogger(getClass());
	protected long baseCrawlRateMillis = 2000L;
	protected BaseRobotRules rules;
	private final int batchUpdateThreshold = 200;

	protected final Set<String> potentialUrls = Collections.synchronizedSet(new TreeSet<>(String.CASE_INSENSITIVE_ORDER));
	private final Set<String> dealerUrls = Collections.synchronizedSet(new TreeSet<>(String.CASE_INSENSITIVE_ORDER));
	private long previousCount = 0;
	private int created = 0, existing = 0;

	@Autowired protected com.findupon.commons.repository.datasource.DataSourceRepo dataSourceRepo;
	@Autowired protected com.findupon.commons.utilities.AutomobileAttributeMatcher attributeMatcher;
	@Autowired private com.findupon.commons.utilities.SlackMessenger slackMessenger;
	private com.findupon.commons.learning.AssetRecognizer assetRecognizer = null;


	/**
	 * The one purpose of anything that extends this is to add to dealerUrls using addDealerUrl through this method
	 */
	protected abstract void obtainDatasourceUrls();

	protected abstract String getSourceName();

	protected abstract boolean verifyAssetType();


	public void run() {
		Stopwatch stopwatch = Stopwatch.createStarted();
		previousCount = dataSourceRepo.count();
		try {
			obtainDatasourceUrls();
			if(!dealerUrls.isEmpty()) {
				createOrUpdateBatch();
			}
			slackMessenger.sendMessageWithArgs("Dealer Retrieval (%s) run complete. %n```" +
							"Previous total:  [%d] %n" +
							"New total:       [%d] %n" +
							"Time taken:      [%s] ```",
					getSourceName(), previousCount, dataSourceRepo.count(), com.findupon.commons.utilities.TimeUtils.format(stopwatch));
		} catch(Exception e) {
			logger.error(red("[AbstractDealerRetrievalBot] - Unhandled exception during [{}] run! Time taken [{}]"), getSourceName(), com.findupon.commons.utilities.TimeUtils.format(stopwatch), e);
		}
	}

	protected void addDealerUrl(String potentialUrl) {
		if(StringUtils.isEmpty(potentialUrl)) {
			return;
		}
		if(potentialUrls.add(potentialUrl)) {
			String url = com.findupon.commons.searchparty.ScoutServices.parseByProtocolAndHost(potentialUrl);
			if(url != null) {
				// search the pre-redirected url first to save a connection if we already have it
				if(dataSourceRepo.searchByUrl(url).isEmpty()) {
					url = connectAndVerifyDealerUrl(url);
					if(url != null && dealerUrls.add(url)) {
						logger.debug("[AbstractDealerRetrievalBot] - Connected & verified dealer: [{}] ", url);
					}
					synchronized(dealerUrls) {
						if(dealerUrls.size() >= batchUpdateThreshold) {
							createOrUpdateBatch();
							dealerUrls.clear();
						}
					}
				} else {
					logger.debug("[AbstractDealerRetrievalBot] - URL already existing in DB, not adding [{}] ", url);
				}
			}
		}
	}

	private void createOrUpdateBatch() {
		for(String dataSourceUrl : dealerUrls) {
			List<DataSource> dataSources = dataSourceRepo.searchByUrl(dataSourceUrl);
			if(dataSources.isEmpty()) {
				dataSourceRepo.save(com.findupon.commons.entity.datasource.DataSource.createNew(dataSourceUrl, com.findupon.commons.entity.datasource.AssetType.AUTOMOBILE, com.findupon.commons.entity.datasource.DataSourceType.GENERIC));
				created++;
				if(created % 25 == 0) {
					logger.debug("[AbstractDealerRetrievalBot] - Created [{}]", created);
				}
			} else {
				existing++;
			}
		}
		logger.info("[AbstractDealerRetrievalBot] - {} run stats:\n" +
						"Created:    [{}]\n" +
						"Existing:   [{}]\n" +
						"Old Total:  [{}]\n" +
						"New Total:  [{}]",
				getSourceName(), created, existing, previousCount, dataSourceRepo.count());
	}

	private String connectAndVerifyDealerUrl(String url) {
		// make sure the url is valid
		if(!UrlValidator.getInstance().isValid(url)) {
			return null;
		}
		if(!com.findupon.commons.searchparty.ScoutServices.clearToVisitDomain(url)) {
			return null;
		}
		if(!com.findupon.commons.searchparty.ScoutServices.acceptedDomainExtension(url)) {
			return null;
		}
		// connect to the url allowing re-directs and grab the new location
		Document potentialDealerSite = com.findupon.commons.netops.ConnectionAgent.INSTANCE.stealthDownload(url).getDocument();
		if(potentialDealerSite == null) {
			return null;
		}
		url = com.findupon.commons.searchparty.ScoutServices.parseByProtocolAndHost(potentialDealerSite.location());

		// check the redirect again for basic validity
		if(!com.findupon.commons.searchparty.ScoutServices.clearToVisitDomain(url)) {
			return null;
		}
		if(!com.findupon.commons.searchparty.ScoutServices.acceptedDomainExtension(url)) {
			return null;
		}
		if(!verifyAssetType()) {
			return url;
		}
		if(isAutomobileAsset(potentialDealerSite)) {
			return url;
		} else {
			return null;
		}
	}

	private boolean isAutomobileAsset(Document potentialDealerSite) {
		if(assetRecognizer == null) {
			assetRecognizer = new com.findupon.commons.learning.AssetRecognizer();
			com.findupon.commons.utilities.SpringUtils.autowire(assetRecognizer);
		}
		Optional<AssetType> assetType = assetRecognizer.determineAssetType(potentialDealerSite);
		return assetType.isPresent() && com.findupon.commons.entity.datasource.AssetType.AUTOMOBILE.equals(assetType.get());
	}

	protected Document connectAndDownload(String url, BaseRobotRules rules, long baseCrawlRateMillis) {
		if(!rules.isAllowed(url)) {
			logger.warn(com.findupon.commons.utilities.ConsoleColors.red("[{}] - URL not allowed by robots.txt [{}]"), getSourceName(), url);
			return null;
		}
		logger.debug("[{}] - Connecting to page [{}]", getSourceName(), url);
		Document document = com.findupon.commons.netops.ConnectionAgent.INSTANCE.stealthDownload(url).getDocument();
		if(document == null) {
			logger.warn("[{}] - Null document returned! [{}]", getSourceName(), url);
		}
		try {
			Thread.sleep(baseCrawlRateMillis);
		} catch(InterruptedException e) {
			Thread.currentThread().interrupt();
			logger.warn("[{}] - Thread interrupted!", getSourceName());
		}
		return document;
	}
}
