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

package com.findupon.cluster.master;

import com.google.common.base.Stopwatch;
import com.findupon.cluster.entity.ClusterTransmission;
import com.findupon.cluster.entity.SentRequest;
import com.findupon.cluster.entity.exceptions.DataSourceRunFailureException;
import com.findupon.cluster.entity.master.MasterMessage;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.findupon.cluster.entity.master.MasterNodeObjects.*;


@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ListingDataSourceRunner implements Runnable {
	private static final Logger logger = LoggerFactory.getLogger(ListingDataSourceRunner.class);

	@Autowired private com.findupon.commons.utilities.DataSourceOperations dataSourceOperations;
	@Autowired private com.findupon.commons.repository.datasource.ListingDataSourceUrlService urlService;
	@Autowired private JdbcTemplate jdbcTemplate;
	@Autowired private com.findupon.commons.utilities.SlackMessenger slackMessenger;

	private final Set<String> productUrls = new LinkedHashSet<>();
	private final int defaultMaxQueuedOrders = 25;
	private final int buildUrlDelegationSize = 200;
	private int indexDelSize = 32;

	com.findupon.commons.entity.datasource.DataSource listingDataSource;


	@Override
	public void run() {
		if(listingDataSource == null) {
			logger.error("The listing data source must be set first!", new UnsupportedOperationException());
			return;
		}
		if(listingDataSource.isGeneric()) {
			logger.error("The data source must have a bot class to be run as listing!", new UnsupportedOperationException());
			return;
		}
		Stopwatch stopwatch = Stopwatch.createStarted();
		Stack<String> baseUrls;

		if(listingDataSource.getIndexOnly()) {
			if(listingDataSource.getIndexDelegationSize() != null && listingDataSource.getIndexDelegationSize() > 0) {
				indexDelSize = listingDataSource.getIndexDelegationSize();
			}
			logger.info("[ListingDataSourceRunner] - Indexed delegation size [{}] for [{}]", indexDelSize, listingDataSource.getUrl());
			try {
				baseUrls = getBaseUrls();
				logger.info("[ListingDataSourceRunner] - Base URLs collection size [{}] for [{}]", baseUrls.size(), listingDataSource.getUrl());

				delegate(() -> {
					List<String> baseUrlList = new ArrayList<>();
					for(int x = 0; x < indexDelSize && !baseUrls.empty(); x++) {
						baseUrlList.add(baseUrls.pop());
					}
					urlService.updateRan(listingDataSource, baseUrlList, true, true);
					return baseUrlList;
				}, baseUrls, MasterMessage.LISTING_GATHER);

				if(checkAndHandleInterrupt(MasterMessage.LISTING_GATHER)) {
					return;
				}
				// clear out the urls as we are now done with them
				urlService.deleteAllUrls(listingDataSource, true);

				// remove all products that were not picked up from the index
				handleRemoval();
			} catch(Exception e) {
				handleRunException(e);
				return;
			}
		} else {
			List<String> newProductUrls = urlService.findAllNotRanUrls(listingDataSource, false);
			if(newProductUrls.isEmpty()) {
				try {
					baseUrls = getBaseUrls();

					delegate(() -> {
						List<String> urlList = Collections.singletonList(baseUrls.pop());
						urlService.updateRan(listingDataSource, urlList, true, true);
						return urlList;
					}, baseUrls, MasterMessage.LISTING_GATHER);
					if(checkAndHandleInterrupt(MasterMessage.LISTING_GATHER)) {
						return;
					}
					// clear out the base urls as we are now done with them
					urlService.deleteAllUrls(listingDataSource, true);

					// grab the new urls
					newProductUrls = urlService.findAllNotRanUrls(listingDataSource, false);

				} catch(Exception e) {
					handleRunException(e);
					return;
				}
			} else {
				logger.info("[ListingDataSourceRunner] - Resuming previous build for [{}] with [{}] URLs",
						listingDataSource.getUrl(), newProductUrls.size());
			}
			try {
				generateUrlsToBuild(newProductUrls);
				delegate(this::getAndUpdateUrlsToWork, productUrls, MasterMessage.LISTING_BUILD);
				if(checkAndHandleInterrupt(MasterMessage.LISTING_BUILD)) {
					return;
				}
				urlService.deleteAllUrls(listingDataSource, false);
			} catch(Exception e) {
				handleRunException(e);
				return;
			}
		}
		// great success
		listingDataSource.setStatus(com.findupon.commons.entity.datasource.DataSourceStatus.SUCCESS);
		listingDataSource.setStatusReason(null);
		listingDataSource.setDetails(null);
		dataSourceOperations.endDataSourceRun(listingDataSource);

		slackMessenger.sendMessageWithArgs("*[ListingDataSourceRunner]* - %s full run completed:%n```Time taken:  [%s]%nRun status:  [%s]```",
				listingDataSource.getUrl(), com.findupon.commons.utilities.TimeUtils.format(stopwatch), com.findupon.commons.entity.datasource.DataSourceStatus.SUCCESS.name());
	}

	private boolean checkAndHandleInterrupt(MasterMessage currentOrder) {
		if(Thread.currentThread().isInterrupted()) {
			List<String> urlsToSetNotRan = new ArrayList<>();
			for(SentRequest s : getSentWorkRequestsSnapshot(listingDataSource, currentOrder)) {
				urlsToSetNotRan.addAll(s.getTransmission().getUrlsToWork());
			}
			for(ClusterTransmission c : getWorkQueueSnapshot()) {
				if(c.getDataSource() != null && c.getDataSource().equals(listingDataSource)) {
					urlsToSetNotRan.addAll(c.getUrlsToWork());
				}
			}
			logger.info("[ListingDataSourceRunner] - Resetting [{}] URLs as not ran from shutdown for [{}]",
					urlsToSetNotRan.size(), listingDataSource.getUrl());
			boolean base = MasterMessage.LISTING_GATHER.equals(currentOrder);
			urlService.updateRan(listingDataSource, urlsToSetNotRan, false, base);
			return true;
		}
		return false;
	}

	private void handleRunException(Exception e) {
		if(e instanceof DataSourceRunFailureException) {
			listingDataSource.setTemporaryDisable(true);
		}
		listingDataSource.setStatus(com.findupon.commons.entity.datasource.DataSourceStatus.FAILURE);
		listingDataSource.setStatusReason(com.findupon.commons.entity.datasource.DataSourceStatusReason.EXCEPTION);
		listingDataSource.setDetails(ExceptionUtils.getStackTrace(e));

		slackMessenger.sendMessageWithArgs("*[ListingDataSourceRunner]* - Error during [%s] run. Check data_source (id=%d) for details",
				listingDataSource.getUrl(), listingDataSource.getId());
		dataSourceOperations.endDataSourceRun(listingDataSource);
	}

	private void delegate(Supplier<Collection<String>> delegator, Collection<String> delegation, MasterMessage message) throws DataSourceRunFailureException {
		int total = delegation.size(), sentSize = 0, queueSize = 0, statCounter = 0;

		while(!delegation.isEmpty() || queueSize > 0 || sentSize > 0) {
			queueSize = getQueuedListingDataSourcesSnapshot(listingDataSource.getId()).size();
			sentSize = getSentWorkRequestsSnapshot(listingDataSource, message).size();
			logger.debug("[ListingDataSourceRunner] - Listing queue size [{}] Listing sent size [{}] bot [{}]", queueSize, sentSize, listingDataSource.getUrl());

			Integer maxQueuedOrders = listingDataSource.getMaxQueuedOrders();
			if(maxQueuedOrders == null || maxQueuedOrders <= 0) {
				maxQueuedOrders = defaultMaxQueuedOrders;
			}
			// listing runs cap total running per site by limiting orders in the work queue
			while(queueSize + sentSize <= maxQueuedOrders) {
				if(!delegation.isEmpty()) {
					if(Thread.currentThread().isInterrupted()) {
						logger.warn("[ListingDataSourceRunner] - Thread interrupt triggered from delegation (most likely shutdown), aborting [{}] run", listingDataSource.getUrl());
						return;
					}
					ClusterTransmission workOrder = new ClusterTransmission();
					workOrder.setDataSource(listingDataSource);
					workOrder.setMessage(message);
					workOrder.setUrlsToWork(new ArrayList<>(delegator.get()));
					workQueue.offer(workOrder);
					queueSize++;
				} else {
					break;
				}
			}
			if(++statCounter % 4 == 0) {
				statCounter = 0;
				int adjustedSize = delegation.size(), adjustedTotal = total;
				// calculate the actual amount of work remaining (only for logging) based on the batch size for build and index gather orders
				// this step is not required for non-index only listing gather orders as only a single base url is sent
				switch(message) {
					case LISTING_BUILD:
						adjustedSize = adjustedSize / buildUrlDelegationSize + (adjustedSize % buildUrlDelegationSize == 0 ? 0 : 1);
						adjustedTotal = adjustedTotal / buildUrlDelegationSize + (adjustedTotal % buildUrlDelegationSize == 0 ? 0 : 1);
						break;
					case LISTING_GATHER:
						if(listingDataSource.getIndexOnly()) {
							adjustedSize = adjustedSize / indexDelSize + (adjustedSize % indexDelSize == 0 ? 0 : 1);
							adjustedTotal = adjustedTotal / indexDelSize + (adjustedTotal % indexDelSize == 0 ? 0 : 1);
						}
						break;
				}
				int completed = Math.abs(adjustedSize - adjustedTotal + queueSize) - sentSize;
				logger.info("[ListingDataSourceRunner] - Completed [{}/{}] {} work orders for [{}]", completed, adjustedTotal, message.name(), listingDataSource.getUrl());
			}
			if(hasDataSourceFailedMoreThanAllowed(listingDataSource)) {
				throw new DataSourceRunFailureException("Max failed work orders reached");
			}
			try {
				Thread.sleep(TimeUnit.SECONDS.toMillis(10));
			} catch(InterruptedException e) {
				Thread.currentThread().interrupt();
				logger.warn("[ListingDataSourceRunner] - Thread interrupt triggered from wait (most likely shutdown), aborting [{}] run", listingDataSource.getUrl());
				return; // no need to do anything more here, DataSourceOperations will update the data source properly
			}
		}
	}

	private List<String> getAndUpdateUrlsToWork() {
		List<String> urlsToWork = new ArrayList<>();
		short x = 0;
		synchronized(productUrls) {
			Iterator<String> iterator = productUrls.iterator();
			while(x < buildUrlDelegationSize && iterator.hasNext()) {
				String url = iterator.next();
				urlsToWork.add(url);
				x++;
			}
			productUrls.removeAll(urlsToWork);
		}
		urlService.updateRan(listingDataSource, urlsToWork, true, false);
		return urlsToWork;
	}

	@SuppressWarnings("unchecked")
	private Stack<String> getBaseUrls() throws Exception {
		Set<String> urls = new LinkedHashSet<>(urlService.findAllNotRanUrls(listingDataSource, true));
		if(urls.isEmpty()) {
			try {
				Class<?> clazz = Class.forName(listingDataSource.getBotClass());
				if(com.findupon.commons.bot.ListingBot.class.isAssignableFrom(clazz)) {
					Object bot = clazz.getConstructor().newInstance();
					com.findupon.commons.utilities.SpringUtils.autowire(bot);
					Method myMethod = clazz.getDeclaredMethod("retrieveBaseUrls");
					myMethod.setAccessible(true);
					urls.addAll((Set<String>)myMethod.invoke(bot));
				}
				logger.info("[BaseUrlRetriever] - Fresh base URLs for [{}] size [{}]", listingDataSource.getUrl(), urls.size());
				urlService.bulkInsert(listingDataSource, urls, true);
			} catch(Exception e) {
				logger.error("[BaseUrlRetriever] - Error retrieving new base URLs for [{}]", listingDataSource.getBotClass(), e);
				throw e;
			}
		} else {
			logger.info("[BaseUrlRetriever] - Resuming previous run for [{}] with [{}] base URLs", listingDataSource.getUrl(), urls.size());
		}
		List<String> temp = new ArrayList<>(urls);
		urls.clear();
		Collections.shuffle(temp);
		Stack<String> urlStack = new Stack<>();
		for(String url : temp) {
			urlStack.push(url);
		}
		return urlStack;
	}

	private void generateUrlsToBuild(List<String> newProductUrls) {
		productUrls.clear();
		Calendar calendar = Calendar.getInstance();
		calendar.add(Calendar.DATE, -listingDataSource.getDaysBetweenRuns());

		Set<String> urlSet = new HashSet<>(
				jdbcTemplate.queryForList("select url from " + getTable() + " p where p.source_url = ? and (p.visited_date is null or p.visited_date < ?)",
						String.class, listingDataSource.getUrl(), calendar.getTime())
		);
		int existingUrlSize = urlSet.size();
		int newProductUrlSize = newProductUrls.size();
		urlSet.addAll(newProductUrls);

		slackMessenger.sendMessageWithArgs("*[ListingDataSourceRunner]* - %s gathering completed:%n```" +
						"Gathered URLs  [%d]%n" +
						"Existing URLs  [%d]%n" +
						"Combined URLs  [%d]%n```",
				listingDataSource.getUrl(), newProductUrlSize, existingUrlSize, urlSet.size());
		productUrls.addAll(urlSet);
	}

	private void handleRemoval() {
		Calendar calendar = Calendar.getInstance();
		calendar.add(Calendar.DATE, -7);
		int removed = jdbcTemplate.update("delete from " + getTable() + " where source_url = ? and visited_date < ?", listingDataSource.getUrl(), calendar.getTime());
		logger.info("[ListingDataSourceRunner] - Removed [{}] products for [{}] that were not indexed", removed, listingDataSource.getUrl());
	}

	// this is bad. change this.
	private String getTable() {
		switch(listingDataSource.getAssetType()) {
			case AUTOMOBILE:
				return "automobile";
			case REAL_ESTATE:
				return "real_estate";
			case WATERCRAFT:
				return "watercraft";
			case AIRCRAFT:
				return "aircraft";
			default:
				throw new UnsupportedOperationException("Bad listing source asset type for removal");
		}
	}
}
