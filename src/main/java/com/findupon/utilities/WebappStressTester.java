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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;

import static com.findupon.commons.utilities.ConsoleColors.green;
import static com.findupon.commons.utilities.ConsoleColors.red;


@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class WebappStressTester {
	private static final Logger logger = LoggerFactory.getLogger(WebappStressTester.class);

	public static void main(String... args) {
		logger.info("Starting stress test...");
		ApplicationContext applicationContext = new ClassPathXmlApplicationContext("classpath*:worker-context.xml");
		WebappStressTester instance = applicationContext.getBean(WebappStressTester.class);
		instance.testSearch();
	}

	private void testSearch() {
		int requests = 1, threads = 1, timeoutMinutes = 10;

		Map<String, Long> requestToConnectionTime = Collections.synchronizedMap(new TreeMap<>(Collections.reverseOrder()));
		List<Future<?>> futures = new ArrayList<>();
		ExecutorService service = Executors.newFixedThreadPool(threads);
		Stopwatch stressTestTimer = Stopwatch.createStarted();

		logger.info(red("Launching stress test making [{}] requests with [{}] workers..."), requests, threads);

		for(int x = 0; x < requests; x++) {
			int yearMin = ThreadLocalRandom.current().nextInt(1955, 2020 + 1);
			int yearMax = ThreadLocalRandom.current().nextInt(yearMin, 2020 + 1);
			int milesMin = ThreadLocalRandom.current().nextInt(0, 250000 + 1);
			int milesMax = ThreadLocalRandom.current().nextInt(milesMin, 250000 + 1);
			int priceMin = ThreadLocalRandom.current().nextInt(1000, 5_000_000 + 1);
			int priceMax = ThreadLocalRandom.current().nextInt(priceMin, 5_000_000 + 1);
			int zipMin = ThreadLocalRandom.current().nextInt(501, 99950 + 1);
			int distance = ThreadLocalRandom.current().nextInt(10, 500 + 1);

			String url = "http://127.0.0.1:8080/cars/for-sale" + // "https://findupon.com/cars/for-sale"
					"?year=" + yearMin + "-" + yearMax +
					"&price=" + priceMin + "-" + priceMax +
					"&miles=" + milesMin + "-" + milesMax +
					"&zip=" + zipMin +
					"&distance=" + distance +
					"&sort=BestDeal";

			futures.add(service.submit(() -> {
				Stopwatch connectionTimer = Stopwatch.createStarted();
				com.findupon.commons.netops.ConnectionAgent.INSTANCE.download(url, com.findupon.commons.netops.entity.ProxyMode.PUBLIC, com.findupon.commons.netops.entity.AgentMode.ROTATE);
				connectionTimer.stop();
				requestToConnectionTime.put(url, connectionTimer.elapsed(TimeUnit.MILLISECONDS));
			}));
		}
		service.shutdown();
		boolean success = true;
		try {
			if(!service.awaitTermination(timeoutMinutes, TimeUnit.MINUTES)) {
				logger.warn("Stress tester worker pool timed out (took longer than [{}] minutes allowed)", timeoutMinutes);
				success = false;
			}
		} catch(InterruptedException e) {
			logger.warn("Stress tester thread interrupt awaiting service termination", e);
			Thread.currentThread().interrupt();
			success = false;
		}
		stressTestTimer.stop();
		if(!success) {
			logger.warn("Cancelling all running futures...");
			futures.stream().filter(f -> !f.isDone()).forEach(f -> {
				if(!f.cancel(true)) {
					logger.warn("Future [{}] failed to cancel", f.toString());
				}
			});
		}

		long totalConnectionTimeMillis = requestToConnectionTime.entrySet().stream().mapToLong(Map.Entry::getValue).sum();
		float totalConnectionTimeSeconds = totalConnectionTimeMillis / 1000F;

		String printout = String.format(
				"\n\n\n\n" + red("***************** Webapp stress test complete *****************") + "\n\n" +
						"Total test time:            %s\n" +
						"Total requests made:        %s\n" +
						"Requests/ second:           %s\n" +
						"Total connection time:      %s seconds\n" +
						"Average connection time:    %s seconds\n" +
						"\n\n",
				green(com.findupon.commons.utilities.TimeUtils.format(stressTestTimer)),
				green(String.format("%,d", requests)),
				green(String.format("%.2f", requests / totalConnectionTimeSeconds)),
				green(String.format("%.2f", totalConnectionTimeSeconds)),
				green(String.format("%.2f", totalConnectionTimeSeconds / requests))
		);
		logger.info(printout);
		try {
			Thread.sleep(100); // ensure printout is last
		} catch(InterruptedException e) {
			Thread.currentThread().interrupt();
			e.printStackTrace();
		}
		System.exit(0);
	}
}
