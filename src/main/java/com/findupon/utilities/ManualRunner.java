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

package com.findupon.utilities;

import com.google.common.base.Stopwatch;
import com.findupon.commons.bot.aircraft.AsoBot;
import com.findupon.datasource.bot.AbstractDealerRetrievalBot;
import com.findupon.datasource.bot.AbstractImportProcess;
import com.findupon.datasource.bot.automotive.CarsDirectProcess;
import com.findupon.datasource.bot.automotive.IntrospectiveDealerRetrievalBot;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;


@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ManualRunner {
	private static final Logger logger = LoggerFactory.getLogger(ManualRunner.class);
	@Value("${production}") private Boolean production;
	@Autowired private com.findupon.commons.repository.datasource.DataSourceRepo dataSourceRepo;


	public static void main(String... args) {
		logger.info("[ManualRunner] - Starting...");
		ApplicationContext applicationContext = new ClassPathXmlApplicationContext("classpath*:worker-context.xml");
		ManualRunner instance = applicationContext.getBean(ManualRunner.class);
		instance.logStart();

		// instance.testDealerRetrievalBot();
		// instance.testCreation();
		// instance.testFullRun();
		// instance.testListingBot();
		// instance.testWatercraftListingBot();
		// instance.testLoadProcess();
		// instance.foo();

		System.exit(0);
	}

	private void foo() {

	}

	private void controllerBot() {
		com.findupon.commons.bot.aircraft.ControllerBot bot = new com.findupon.commons.bot.aircraft.ControllerBot();
		com.findupon.commons.utilities.SpringUtils.autowire(bot);
		bot.buildProduct("https://www.controller.com/listings/aircraft/for-sale/24616455/2004-learjet-45xr");
	}

	/*

		TODO ***************

		take into account vast listings and lemon free (sites that redirect you to a random page)
		and don't refresh them

		maybe do this programmatically by checking for redirect? and manual by checking the url?

	 */

	private void testListingBot() {
		com.findupon.commons.bot.aircraft.AbstractAircraftBot bot = new AsoBot();
		com.findupon.commons.utilities.SpringUtils.autowire(bot);

		// String url = "https://www.trulia.com/for_sale/04290_zip/1_p/";
		String url = "https://www.aso.com/listings/spec/ViewAd.aspx?id=174897";

		bot.buildProduct(url);
	}

	private void testWatercraftListingBot() {
		com.findupon.commons.bot.watercraft.ListingWatercraftBot bot = new com.findupon.commons.bot.watercraft.YachtWorldBot();
		com.findupon.commons.utilities.SpringUtils.autowire(bot);

		bot.retrieveBaseUrls();
	}

	private void testLoadProcess() {
		AbstractImportProcess process = new CarsDirectProcess();
		com.findupon.commons.utilities.SpringUtils.autowire(process);
		process.init();
	}

	private void testDealerRetrievalBot() {
		AbstractDealerRetrievalBot bot = new IntrospectiveDealerRetrievalBot();
		com.findupon.commons.utilities.SpringUtils.autowire(bot);
		bot.run();
	}

	private void testCreation() {
		com.findupon.commons.searchparty.AutomotiveGatherer gatherer = new com.findupon.commons.searchparty.AutomotiveGatherer();
		com.findupon.commons.utilities.SpringUtils.autowire(gatherer);
		List<String> testUrls = Arrays.asList(
				"http://sarasota-ford.com/Tampa/For-Sale/New/Ford/Explorer/2018-XLT-White-SUV/54542263/"
		);
		long total = 0, download = 0, build = 0;
		int x = 0;
		for(String url : testUrls) {
			Stopwatch stopwatch = Stopwatch.createStarted();
			Document document = com.findupon.commons.netops.ConnectionAgent.INSTANCE.stealthDownload(url).getDocument();
			long singleDownload = stopwatch.elapsed(TimeUnit.MILLISECONDS);
			long singleBuild = 0L;
			download += singleDownload;
			if(document != null) {
				Stopwatch buildTimer = Stopwatch.createStarted();
				com.findupon.commons.entity.product.automotive.Automobile automobile = gatherer.buildProduct(document);
				if(automobile == null) {
					logger.warn(com.findupon.commons.utilities.ConsoleColors.red("Automobile came back null! [{}]"), url);
				}
				singleBuild = buildTimer.elapsed(TimeUnit.MILLISECONDS);
				build += singleBuild;
			} else {
				logger.warn(com.findupon.commons.utilities.ConsoleColors.red("Document came back null! [{}]"), url);
			}
			long singleTotal = stopwatch.elapsed(TimeUnit.MILLISECONDS);
			total += singleTotal;
			x++;
			logger.info("Single time taken. Download: [{}] Build: [{}] Total: [{}]",
					com.findupon.commons.utilities.TimeUtils.formatConditionalSeconds(singleDownload),
					com.findupon.commons.utilities.TimeUtils.formatConditionalSeconds(singleBuild),
					com.findupon.commons.utilities.TimeUtils.formatConditionalSeconds(singleTotal));
		}
		logger.info("Full time taken from {} test{}. Download (avg): [{}] Build (avg): [{}] Total: [{}]\n",
				x, x > 1 ? "s" : "",
				com.findupon.commons.utilities.TimeUtils.formatConditionalSeconds(download / x),
				com.findupon.commons.utilities.TimeUtils.formatConditionalSeconds(build / x),
				com.findupon.commons.utilities.TimeUtils.formatConditionalSeconds(total / x));
		try {
			Thread.sleep(100L);
		} catch(InterruptedException e) {
			e.printStackTrace();
		}
	}

	private void testFullRun() {
		List<String> testUrls = Arrays.asList(
				"https://www.carmanchryslerjeepdodge.com/"
		);
		for(String url : testUrls) {
			com.findupon.commons.searchparty.AbstractProductGatherer gatherer = new com.findupon.commons.searchparty.AutomotiveGatherer();
			com.findupon.commons.utilities.SpringUtils.autowire(gatherer);
			com.findupon.commons.entity.datasource.DataSource dataSource = dataSourceRepo.findByExactUrl(url);
			if(dataSource == null) {
				System.err.printf("Could not find datasource in the database, creating temporary datasource in memory: %s\n", url);
				dataSource = com.findupon.commons.entity.datasource.DataSource.createNew(url, com.findupon.commons.entity.datasource.AssetType.AUTOMOBILE, com.findupon.commons.entity.datasource.DataSourceType.GENERIC);
			} else if(dataSource.getPermanentDisable()) {
				System.err.printf("\n********* WARNING *********\n\nYou are attempting to run a datasource [%s] that has been permanently disabled. " +
						"If you really need to do this, update the db.\n", dataSource.getUrl());
				continue;
			}
			dataSource.setProxyMode(com.findupon.commons.netops.entity.ProxyMode.ROTATE_LOCATION);
			dataSource.setAgentMode(com.findupon.commons.netops.entity.AgentMode.ROTATE);
			gatherer.initiate(dataSource, 0);
		}
	}

	private void logStart() {
		logger.info("[ManualRunner] - Start completed. Currently active schema [{}] \nCurrent heap size: [{}] Maximum heap size: [{}] JVM bit size: [{}]",
				production ? "prod" : "dev",
				MemoryUtils.getCurrentHeapSizeStr(),
				MemoryUtils.getMaxHeapSizeStr(),
				System.getProperty("sun.arch.data.model"));
	}
}
