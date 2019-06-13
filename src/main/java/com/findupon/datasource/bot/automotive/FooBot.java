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

import com.plainviewrd.commons.netops.entity.*;
import com.plainviewrd.datasource.bot.AbstractDealerRetrievalBot;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

import static com.plainviewrd.commons.utilities.JsoupUtils.firstChild;


@Component
public class FooBot extends AbstractDealerRetrievalBot {

	private final List<Future<?>> futures = Collections.synchronizedList(new ArrayList<>());


	@Override
	protected void obtainDatasourceUrls() {
		ExecutorService service = Executors.newFixedThreadPool(8);
		Document document = com.plainviewrd.commons.netops.ConnectionAgent.INSTANCE.xmlDownload("https://motominer.com/sitemap2.xml", com.plainviewrd.commons.netops.entity.ProxyMode.ROTATE_LOCATION, com.plainviewrd.commons.netops.entity.AgentMode.ROTATE);

		if(document == null) {
			logger.warn("Root document came back null for the foo bot");
			return;
		}
		Set<String> urlsToVisit = document.select("loc").stream()
				.distinct()
				.map(Element::text)
				.map(s -> com.plainviewrd.commons.searchparty.ScoutServices.encodeSpacing(s, true))
				.collect(Collectors.toSet());

		logger.info("URLs to visit size [{}]", urlsToVisit.size());

		int visited = 0, potential = 0;
		for(String url : urlsToVisit) {
			if(++visited % 100 == 0) {
				LongAdder progress = new LongAdder();
				LongAdder done = new LongAdder();
				synchronized(futures) {
					futures.forEach(f -> {
						if(f.isDone()) {
							done.increment();
						} else {
							progress.increment();
						}
					});
				}
				logger.info("Base site visited: [{}] Total potential: [{}] In progress/ queued futures: [{}] Completed futures: [{}]",
						visited, potential, progress.longValue(), done.longValue());
				try {
					Thread.sleep(3000L);
				} catch(InterruptedException e) {
					Thread.currentThread().interrupt();
					logger.warn("Interrupt during foobot wait... this should not happen", e);
				}
			}
			com.plainviewrd.commons.netops.entity.AgentResponse response = com.plainviewrd.commons.netops.ConnectionAgent.INSTANCE.stealthDownload(url);
			com.plainviewrd.commons.netops.entity.AgentDecision decision = response.getDecision();

			if(com.plainviewrd.commons.netops.entity.RequestedAction.ABORT_CRAWL.equals(decision.getAction())) {
				logger.error("Decision made to abort gathering. Status code [{}] message [{}]", decision.getStatusCode(), decision.getMessage());
				synchronized(futures) {
					futures.forEach(f -> f.cancel(true));
				}
				return;
			}
			Document dealerPage = response.getDocument();
			if(dealerPage != null) {
				Element dealerAnchor = firstChild(dealerPage.select("a[class=dealer-url]"));
				if(dealerAnchor != null) {
					String dealerUrl = dealerAnchor.attr("href");
					if(StringUtils.isNotBlank(dealerUrl)) {
						potential++;
						futures.add(service.submit(() -> addDealerUrl(dealerUrl.toLowerCase())));
					}
				}
			} else {
				logger.warn("Document came back null from dealer page [{}]", url);
			}
		}
		service.shutdown();
		try {
			service.awaitTermination(7, TimeUnit.DAYS);
		} catch(InterruptedException e) {
			Thread.currentThread().interrupt();
			logger.warn("Interrupt awaiting foobot termination... this should not happen", e);
		}
	}

	@Override
	protected String getSourceName() {
		return "Foo";
	}

	@Override
	protected boolean verifyAssetType() {
		return false;
	}
}
