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

import com.findupon.commons.utilities.NetworkUtils;
import com.maxmind.geoip.Location;
import com.maxmind.geoip.LookupService;
import com.findupon.commons.netops.entity.Proxy;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;


public final class LocationUtils {
	private static final LookupService lookupService = loadLookupService();
	private static final Map<String, Location> ipLocationCache = Collections.synchronizedMap(new HashMap<>());
	private static final Set<String> local = new HashSet<>(Arrays.asList("127.0.0.1", "0:0:0:0:0:0:0:1"));
	private static Location currentLocation;
	private static Proxy currentClosestProxy;


	private static LookupService loadLookupService() {
		URL url = LocationUtils.class.getResource("/geo-city.dat");
		File geo = new File(url.getFile());
		try {
			return new LookupService(geo, LookupService.GEOIP_MEMORY_CACHE | LookupService.GEOIP_CHECK_CACHE);
		} catch(IOException e) {
			throw new RuntimeException("Could not load geo-city.dat resource", e);
		}
	}

	public static synchronized Location getCurrentLocation() {
		if(currentLocation == null) {
			currentLocation = getLocation(NetworkUtils.getExternalIp());
		}
		return currentLocation;
	}

	public static Location getLocation(String ipAddress) {
		if(StringUtils.isBlank(ipAddress)) {
			return null;
		}
		return ipLocationCache.computeIfAbsent(ipAddress, l -> lookupService.getLocation(ipAddress));
	}

	public static Proxy findClosestProxyToCurrentLocation() {
		if(currentClosestProxy == null) {
			currentClosestProxy = findClosestProxy(getCurrentLocation());
		}
		return currentClosestProxy;
	}

	public static Proxy findClosestProxy(Location location) {
		if(location == null) {
			return null;
		}
		Proxy closest = null;
		double minDistance = Double.MAX_VALUE;
		for(Proxy proxy : Proxy.getPrivateProxies()) {
			double dist = location.distance(proxy.getLocation());
			if(dist < minDistance) {
				closest = proxy;
				minDistance = dist;
			}
		}
		return closest;
	}

	public static String getRegion(String ipAddress) {
		if(StringUtils.isBlank(ipAddress)) {
			return null;
		}
		Location location = getLocation(ipAddress);
		if(location != null) {
			return location.region;
		} else {
			return null;
		}
	}

	public static Set<String> getLocal() {
		return local;
	}

	public static String getFormattedLatLon(float latitude, float longitude) {
		try {
			int latSeconds = Math.round(latitude * 3600);
			int latDegrees = latSeconds / 3600;
			latSeconds = Math.abs(latSeconds % 3600);
			int latMinutes = latSeconds / 60;
			latSeconds %= 60;

			int longSeconds = Math.round(longitude * 3600);
			int longDegrees = longSeconds / 3600;
			longSeconds = Math.abs(longSeconds % 3600);
			int longMinutes = longSeconds / 60;
			longSeconds %= 60;
			String latDegree = latDegrees >= 0 ? "N" : "S";
			String lonDegrees = longDegrees >= 0 ? "E" : "W";

			return Math.abs(latDegrees) + "°" + latMinutes + "'" + latSeconds + "" + "\"" + latDegree + " " +
					Math.abs(longDegrees) + "°" + longMinutes + "'" + longSeconds + "\"" + lonDegrees;
		} catch(Exception e) {
			return String.format("%8.5f", latitude) + " " + String.format("%8.5f", longitude);
		}
	}
}
