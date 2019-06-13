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

package com.findupon.commons.netops.entity;

import com.maxmind.geoip.Location;
import com.findupon.commons.utilities.LocationUtils;
import com.findupon.utilities.PropertyLoader;
import org.apache.http.HttpHost;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;


public final class Proxy {
	private static final Logger logger = LoggerFactory.getLogger(Proxy.class);

	private static final int port = PropertyLoader.getInteger("proxy.port");
	private static final Credentials credentials = loadCredentials();
	private static final List<Proxy> privateProxies = loadPrivate();
	private static final Proxy openProxy = loadOpen();

	private final HttpHost host;
	private Location location;


	public static Proxy getByMode(ProxyMode proxyMode) {
		switch(Objects.requireNonNull(proxyMode)) {
			case ROTATE_LOCATION:
				Proxy proxy = LocationUtils.findClosestProxyToCurrentLocation();
				if(proxy == null) {
					proxy = privateProxies.get(0);
					logger.warn("[ProxyLoader] - Could not determine location to find closest proxy, defaulting to [{}]",
							proxy.getHost().getHostName());
				}
				return proxy;
			case ROTATE_OPEN:
				return openProxy;
			default:
				throw new IllegalArgumentException("Get by proxy mode requires a tunneled option");
		}
	}

	public static Credentials getCredentials() {
		return credentials;
	}

	private static Credentials loadCredentials() {
		String proxyUser = PropertyLoader.getString("proxy.user");
		String proxyPass = PropertyLoader.getString("proxy.pass");
		return new UsernamePasswordCredentials(proxyUser, proxyPass);
	}

	private static List<Proxy> loadPrivate() {
		return Arrays.asList(
				new Proxy(new HttpHost("us.proxymesh.com", port), 29.760427F, -95.369803F),
				new Proxy(new HttpHost("us-il.proxymesh.com", port), 41.878114F, -87.629798F),
				new Proxy(new HttpHost("us-dc.proxymesh.com", port), 38.907192F, -77.036871F),
				new Proxy(new HttpHost("us-ny.proxymesh.com", port), 40.712775F, -74.005973F),
				new Proxy(new HttpHost("us-fl.proxymesh.com", port), 25.761680F, -80.19179F),
				new Proxy(new HttpHost("us-wa.proxymesh.com", port), 47.606209F, -122.332071F),
				new Proxy(new HttpHost("us-ca.proxymesh.com", port), 37.338208F, -121.886329F)
		);
	}

	public static List<Proxy> getPrivateProxies() {
		return privateProxies;
	}

	private static Proxy loadOpen() {
		return new Proxy(new HttpHost("open.proxymesh.com", port), 33.7490F, 84.3880F);
	}

	private Proxy(HttpHost host, float latitude, float longitude) {
		this.host = host;
		Location location = new Location();
		location.latitude = latitude;
		location.longitude = longitude;
		this.location = location;
	}

	public HttpHost getHost() {
		return host;
	}

	public Location getLocation() {
		return location;
	}

	public void setLocation(Location location) {
		this.location = location;
	}
}
