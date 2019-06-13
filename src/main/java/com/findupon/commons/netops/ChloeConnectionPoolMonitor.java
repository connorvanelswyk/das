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

package com.findupon.commons.netops;

import com.findupon.commons.netops.entity.RequestMeta;
import com.findupon.commons.utilities.ConsoleColors;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;


/**
 * Relying on the HttpClient to check if a connection is stale before executing a request is expensive and not always reliable.
 * This monitor is also in charge of clearing out old requests from the hostRequestMetaMap.
 */
public class ChloeConnectionPoolMonitor extends Thread {
	private static final Logger logger = LoggerFactory.getLogger(ChloeConnectionPoolMonitor.class);

	private final PoolingHttpClientConnectionManager connectionManager;
	private final Map<String, RequestMeta> hostRequestMetaMap;
	private final AtomicBoolean run = new AtomicBoolean(true);


	ChloeConnectionPoolMonitor(PoolingHttpClientConnectionManager connectionManager, Map<String, RequestMeta> hostRequestMetaMap) {
		super();
		this.connectionManager = connectionManager;
		this.hostRequestMetaMap = hostRequestMetaMap;
		this.setName("cp-monitor");
	}

	@Override
	public void run() {
		AtomicInteger logPoolStats = new AtomicInteger();
		AtomicInteger hostRequestMapClearer = new AtomicInteger();
		while(run.get()) {
			try {
				synchronized(this) {
					wait(TimeUnit.SECONDS.toMillis(10L));
				}
			} catch(InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
			if(run.get()) {
				logger.trace("[ChloeConnectionPoolMonitor] - Closing expired connections...");
				connectionManager.closeExpiredConnections();
				connectionManager.closeIdleConnections(30L, TimeUnit.SECONDS);
				// log every 30 seconds
				if(logPoolStats.incrementAndGet() % 3 == 0) {
					logPoolStats.set(0);
					logger.trace("[ChloeConnectionPoolMonitor] - Stats {}",
							ConsoleColors.green(connectionManager.getTotalStats().toString()));
				}
				// clear expired host requests every 60 seconds
				if(hostRequestMapClearer.incrementAndGet() % 6 == 0) {
					logger.debug("[ChloeConnectionPoolMonitor] - Clearing expired host requests...");
					Set<String> expiredHosts;
					synchronized(hostRequestMetaMap) {
						expiredHosts = hostRequestMetaMap.entrySet().stream()
								.filter(r -> RequestHelper.isRequestStateExpired(r.getValue()))
								.map(Map.Entry::getKey)
								.collect(Collectors.toSet());
						expiredHosts.forEach(hostRequestMetaMap::remove);
					}
					logger.debug("[ChloeConnectionPoolMonitor] - Expired host requests removed [{}]", expiredHosts.size());
					hostRequestMapClearer.set(0);
				}
			}
		}
	}

	public synchronized void shutdown() {
		run.set(false);
		notifyAll();
	}
}