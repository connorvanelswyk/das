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

package com.findupon.commons.utilities;

import com.findupon.cluster.entity.ClusterTransmission;
import com.findupon.cluster.entity.worker.NodeMessage;
import com.findupon.commons.entity.datasource.DataSource;
import com.findupon.commons.entity.datasource.DataSourceStatus;
import com.findupon.commons.entity.datasource.DataSourceStatusReason;
import com.findupon.commons.repository.datasource.DataSourceRepo;
import org.apache.commons.lang3.BooleanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.atomic.LongAdder;

import static com.findupon.cluster.entity.master.MasterNodeObjects.*;


@Component
public class DataSourceOperations {
	private static final Logger logger = LoggerFactory.getLogger(DataSourceOperations.class);

	@Autowired private DataSourceRepo dataSourceRepo;
	@Autowired private JdbcTemplate jdbcTemplate;

	private final Object mutex = new Object();
	private final int MAX_DS_FAILURES = 4;


	public boolean hasCapacityToQueue(DataSource dataSource) {
		int runningListings = 0;
		int runningGenerics = 0;

		for(DataSource runningDataSource : getRunningDataSourcesSnapshot()) {
			if(runningDataSource.isGeneric()) {
				runningGenerics++;
			} else {
				runningListings++;
			}
		}
		// if running < queued, we have capacity
		return (!dataSource.isGeneric() && runningListings < MAX_QUEUED_LISTING_DATA_SOURCES) ||
				(dataSource.isGeneric() && runningGenerics < getMaxQueuedGenericDataSources());
	}

	public void handleDataSourceTransmissionResponse(ClusterTransmission transmission) {
		DataSource dataSource = transmission.getDataSource();
		boolean failure = NodeMessage.WORK_START_FAILURE.equals(transmission.getMessage())
				|| NodeMessage.WORK_FINISH_FAILURE.equals(transmission.getMessage());

		if(!dataSource.isGeneric() && failure) {
			logger.debug("[DataSourceOperations] - Status [{}] from node [{}] during [{}] run",
					transmission.getMessage().getDirective(), transmission.getNodeId(), dataSource.getUrl());
			failedOrders.computeIfAbsent(dataSource, x -> new LongAdder()).increment();
		}
		if(dataSource.isGeneric()) {
			if(failure) {
				DataSourceStatusReason reason = dataSource.getStatusReason();
				if(reason != null && !DataSourceStatusReason.SHUTDOWN.equals(reason)) {
					if(dataSource.getFailedAttempts() == null) {
						dataSource.setFailedAttempts(1);
					} else {
						dataSource.setFailedAttempts(dataSource.getFailedAttempts() + 1);
						if(dataSource.getFailedAttempts() >= MAX_DS_FAILURES) {
							dataSource.setPermanentDisable(true);
						}
					}
					// happens if we encounter distil or bot managed site, not worth crawling them again
					if(DataSourceStatusReason.BLOCKED.equals(reason)) {
						dataSource.setPermanentDisable(true);
					}
					if(BooleanUtils.toBoolean(dataSource.getPermanentDisable())) {
						int removed = jdbcTemplate.update("delete from automobile where source_url is null and dealer_url = ?", dataSource.getUrl());
						logger.warn("Data source [{}] has failed {} times with reason [{}]. Setting as disabled and transitioning [{}] products to history",
								dataSource.getUrl(), dataSource.getFailedAttempts(), reason.toString(), removed);
					}
				}
				dataSource.setStatus(DataSourceStatus.FAILURE);
			} else {
				dataSource.setStatus(DataSourceStatus.SUCCESS);
				dataSource.setFailedAttempts(null);
				dataSource.setStatusReason(null);
				dataSource.setDetails(null);
			}

			if(master.getRunning()) {
				// end the run as generic's are one-and-done
				endDataSourceRun(dataSource);
			}
		}

		// if it's a listing data source work order and it failed, nothing else needed to do here
		// ending a run for a ListingDataSource should only be done in the ListingDataSourceRunner
		// in the future we can implement features to add it back to the work queue in those cases
		// this will need a counter so bad request don't get stuck in a loop

		// if it's a listing data source work order and it's successful, nothing else needed to do here
		// the workQueue has been polled and the node will transition to the waiting pool allowing it to take new requests
	}

	public boolean transitionToRunning(DataSource dataSource) {
		logger.trace("[DataSourceOperations] - Locking data sources to transition to running...");
		synchronized(mutex) {
			if(runningDataSources.contains(dataSource)) {
				logger.error("[DataSourceOperations] - Data source already running; lock released [{}]", dataSource.getUrl(),
						new UnsupportedOperationException());
				return false;
			}
			runningDataSources.add(dataSource);
			dataSource.setRunning(true);
			dataSource.setStaged(false);
			dataSourceRepo.saveAndFlush(dataSource);
			logger.trace("[DataSourceOperations] - Data source [{}] transitioned to running; lock released", dataSource.getUrl());
			return true;
		}
	}

	public void endDataSourceRun(DataSource dataSource) {
		endDataSourceRun(dataSource, true);
	}

	public void endDataSourceRun(DataSource dataSource, boolean lock) {
		if(lock) {
			logger.trace("[DataSourceOperations] - Locking to end run...");
			synchronized(mutex) {
				internalEndDataSourceRun(dataSource);
			}
			logger.trace("[DataSourceOperations] - Lock released");
		} else {
			internalEndDataSourceRun(dataSource);
		}
	}

	private void internalEndDataSourceRun(DataSource dataSource) {
		boolean success = DataSourceStatus.SUCCESS.equals(dataSource.getStatus());
		if(!runningDataSources.remove(dataSource)) {
			logger.error("Could not end data source run as it didn't exist as running! [{}]", dataSource.getUrl());
			return;
		}
		removeAllQueuedWorkOrders(dataSource);

		if(success) {
			dataSource.setLastRun(new Date());
		} else {
			if(dataSource.isGeneric()) {
				// all generic failures besides shutdown set the last run
				if(Objects.equals(DataSourceStatusReason.SHUTDOWN, dataSource.getStatusReason())) {
					dataSource.setLastRun(null);
				} else {
					dataSource.setLastRun(new Date());
				}
			} else {
				// listing only case, only set last run if its a bad failure
				if(Objects.equals(DataSourceStatusReason.EXCEPTION, dataSource.getStatusReason())
						|| Objects.equals(DataSourceStatusReason.ROBOTS_TXT, dataSource.getStatusReason())
						|| Objects.equals(DataSourceStatusReason.BACKOFF, dataSource.getStatusReason())
						|| Objects.equals(DataSourceStatusReason.BLOCKED, dataSource.getStatusReason())) {
					dataSource.setLastRun(new Date());
				} else {
					dataSource.setLastRun(null);
				}
			}
		}
		if(dataSource.getTemporaryDisable() == null) {
			dataSource.setTemporaryDisable(false);
		}
		if(dataSource.getPermanentDisable() == null) {
			dataSource.setPermanentDisable(false);
		}
		if(dataSource.isGeneric() && dataSource.getIndexOnly() == null) {
			dataSource.setIndexOnly(false);
		}
		dataSource.setStaged(false);
		dataSource.setRunning(false);
		dataSourceRepo.saveAndFlush(dataSource);
		failedOrders.remove(dataSource);

		logger.debug("[DataSourceOperations] - Ended data source run [{}] with status [{}] reason [{}]",
				dataSource.getUrl(), dataSource.getStatus(), dataSource.getStatusReason());
	}

	public void clearStaged() {
		jdbcTemplate.update("update data_source set staged = 0 where staged = 1");
	}

	public void calculateAndUpdateDataSourceStats(DataSource dataSource, long secondsTaken, int productsBuilt,
	                                              long visitedUrls, long analyzedUrls, BigDecimal downloadedMb) {
		if(dataSource.getTotalRuns() == null) {
			dataSource.setTotalRuns(1);
		} else {
			dataSource.setTotalRuns(dataSource.getTotalRuns() + 1);
		}
		dataSource.setLastTime(secondsTaken);
		if(dataSource.getTotalTime() == null) {
			dataSource.setTotalTime(dataSource.getLastTime());
		} else {
			dataSource.setTotalTime(dataSource.getTotalTime() + dataSource.getLastTime());
		}
		dataSource.setLastProductCount(productsBuilt);
		if(dataSource.getTotalProductCount() == null) {
			dataSource.setTotalProductCount((long)dataSource.getLastProductCount());
		} else {
			dataSource.setTotalProductCount(dataSource.getTotalProductCount() + dataSource.getLastProductCount());
		}
		dataSource.setLastVisitedUrls(visitedUrls);
		if(dataSource.getTotalVisitedUrls() == null) {
			dataSource.setTotalVisitedUrls(dataSource.getLastVisitedUrls());
		} else {
			dataSource.setTotalVisitedUrls(dataSource.getTotalVisitedUrls() + dataSource.getLastVisitedUrls());
		}
		dataSource.setLastAnalyzedUrls(analyzedUrls);
		if(dataSource.getTotalAnalyzedUrls() == null) {
			dataSource.setTotalAnalyzedUrls(dataSource.getLastAnalyzedUrls());
		} else {
			dataSource.setTotalAnalyzedUrls(dataSource.getTotalAnalyzedUrls() + dataSource.getLastAnalyzedUrls());
		}
		dataSource.setLastDownloadMb(downloadedMb);
		if(dataSource.getTotalDownloadMb() == null) {
			dataSource.setTotalDownloadMb(dataSource.getLastDownloadMb());
		} else {
			dataSource.setTotalDownloadMb(dataSource.getTotalDownloadMb().add(dataSource.getLastDownloadMb()));
		}
	}
}
