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

package com.findupon.commons.netops.strategies;

import org.apache.http.client.BackoffManager;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.pool.ConnPoolControl;

import java.util.HashMap;
import java.util.Map;


public class ChloeBackoffManager implements BackoffManager {
	private final ConnPoolControl<HttpRoute> connPerRoute;
	private final Map<HttpRoute, Long> lastRouteProbes;
	private final Map<HttpRoute, Long> lastRouteBackoffs;
	private final long coolDown = 5 * 1000L; // wait between adjustments in pool sizes for a given host, to allow enough time for the adjustments to take effect
	private final double backoffFactor = 0.5d; // the new per-host limit will be roughly the current max times this factor
	private final int cap; // absolute maximum per-host connection pool size to probe up to (set to defaultMaxPerRoute)


	public ChloeBackoffManager(ConnPoolControl<HttpRoute> connPerRoute) {
		this.connPerRoute = connPerRoute;
		this.lastRouteProbes = new HashMap<>();
		this.lastRouteBackoffs = new HashMap<>();
		this.cap = connPerRoute.getDefaultMaxPerRoute();
	}

	@Override
	public void backOff(HttpRoute route) {
		synchronized(connPerRoute) {
			int currentMax = connPerRoute.getMaxPerRoute(route);
			Long lastUpdate = getLastUpdate(lastRouteBackoffs, route);
			long now = System.currentTimeMillis();
			if(now - lastUpdate < coolDown) {
				return;
			}
			connPerRoute.setMaxPerRoute(route, getBackedOffPoolSize(currentMax));
			lastRouteBackoffs.put(route, now);
		}
	}

	private int getBackedOffPoolSize(int currentMax) {
		if(currentMax <= 1) {
			return 1;
		}
		return (int)(Math.floor(backoffFactor * currentMax));
	}

	@Override
	public void probe(HttpRoute route) {
		synchronized(connPerRoute) {
			int curr = connPerRoute.getMaxPerRoute(route);
			int max = (curr >= cap) ? cap : curr + 1;
			Long lastProbe = getLastUpdate(lastRouteProbes, route);
			Long lastBackoff = getLastUpdate(lastRouteBackoffs, route);
			long now = System.currentTimeMillis();
			if(now - lastProbe < coolDown || now - lastBackoff < coolDown) {
				return;
			}
			connPerRoute.setMaxPerRoute(route, max);
			lastRouteProbes.put(route, now);
		}
	}

	private Long getLastUpdate(Map<HttpRoute, Long> updates, HttpRoute route) {
		Long lastUpdate = updates.get(route);
		if(lastUpdate == null) {
			lastUpdate = 0L;
		}
		return lastUpdate;
	}
}
