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

package com.findupon.commons.building;

import org.quickgeo.Place;
import org.quickgeo.PlaceFactory;
import org.quickgeo.PostalSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Pattern;


public final class PostalLookupService {

	private static final Logger logger = LoggerFactory.getLogger(PostalLookupService.class);
	private static final Map<String, Place> zipPlaceMap = new HashMap<>();
	private static final Map<String, List<Place>> namePlaceCache = Collections.synchronizedMap(new HashMap<>());

	static {
		try(BufferedReader in = new BufferedReader(new InputStreamReader(PostalSource.class.getResourceAsStream("/US.txt")))) {
			in.lines().forEach(line -> {
				Place p = PlaceFactory.fromLine(line);
				if(p.getPostalCode() == null) {
					logger.error("[PostalLookupService] - No zip found on US postal data");
					return;
				}
				zipPlaceMap.putIfAbsent(p.getPostalCode(), p);
			});
		} catch(Exception e) {
			logger.error("[PostalLookupService] - Error reading source file", e);
		}
	}

	public static List<Place> placesByName(String regex) {
		return namePlaceCache.computeIfAbsent(regex, l -> {
			List<Place> matches = new ArrayList<>();
			Pattern p;
			try {
				p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
			} catch(Exception e) {
				logger.trace("[PostalLookupService] - Invalid regex. This will be improved with better, non-regex look ups");
				return new ArrayList<>();
			}
			for(Place place : zipPlaceMap.values()) {
				if(p.matcher(place.getPlaceName()).matches()) {
					matches.add(place);
				}
			}
			return matches;
		});
	}

	public static Place placeByZip(String zip) {
		return zipPlaceMap.get(zip);
	}

	public static List<Place> getAllPlaces() {
		return new ArrayList<>(zipPlaceMap.values());
	}
}
