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

import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;


@Component
public abstract class AbstractImportProcess<P extends com.findupon.commons.entity.product.Product & Serializable> {
	protected final Logger logger = LoggerFactory.getLogger(getClass());

	private com.findupon.commons.entity.datasource.DataSource currentDataSource = null;

	@Autowired protected JdbcTemplate jdbcTemplate;
	@Autowired protected com.findupon.commons.utilities.SlackMessenger slackMessenger;
	@Autowired private com.findupon.commons.building.ProductUtils productUtils;
	@Autowired private com.findupon.commons.repository.datasource.DataSourceRepo dataSourceRepo;


	public abstract void init();


	protected com.findupon.commons.entity.datasource.DataSource getDataSource() {
		if(currentDataSource == null) {
			currentDataSource = dataSourceRepo.findByBotClass(getClass().getCanonicalName());
			if(!com.findupon.commons.entity.datasource.DataSourceType.IMPORT_PROCESS.equals(currentDataSource.getDataSourceType())) {
				logger.error("[AbstractImportProcess] - Non import process attempted [{}]", getClass().getCanonicalName());
			}
		}
		return currentDataSource;
	}

	protected void setMeta(List<P> products) {
		String dataSourceUrl = getDataSource().getUrl();
		Long dataSourceId = getDataSource().getId();
		products.forEach(p -> {
			p.setSourceUrl(dataSourceUrl);
			p.setDataSourceId(dataSourceId);
		});
		List<List<P>> batched = Lists.partition(products, 25);
		ExecutorService service = Executors.newFixedThreadPool(8);
		List<Future<?>> futures = new ArrayList<>();

		int totalSize = products.size();
		LongAdder completed = new LongAdder();

		batched.forEach(batch -> futures.add(service.submit(() -> batch.forEach(p -> {
			if(!Thread.currentThread().isInterrupted() && !productUtils.basicInvalidator(p)) {
				completed.increment();
				long iteration = completed.longValue();
				if(iteration % (totalSize / 60) == 0 || iteration == totalSize) {
					logger.debug("[{}] - Meta set percent complete [{}]", getClass().getSimpleName(),
							com.findupon.commons.utilities.ConsoleColors.green(String.format("%.2f", ((float)iteration / totalSize) * 100)));
				}
				productUtils.mergeExistingProductAndRefreshAggregates(p);
			}
		}))));
		service.shutdown();
		try {
			if(!service.awaitTermination(8L, TimeUnit.HOURS)) {
				logger.error("[{}] - Meta set service timed out!", getClass().getSimpleName());
				futures.stream().filter(f -> !f.isDone()).forEach(f -> f.cancel(true));
			}
		} catch(InterruptedException e) {
			Thread.currentThread().interrupt();
			logger.warn("[{}] - Meta set service interrupted", getClass().getSimpleName());
			futures.stream().filter(f -> !f.isDone()).forEach(f -> f.cancel(true));
		}
	}
}
