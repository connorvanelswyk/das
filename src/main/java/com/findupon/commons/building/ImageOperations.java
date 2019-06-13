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

import com.findupon.commons.utilities.ConsoleColors;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.apache.commons.validator.routines.UrlValidator;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.*;
import java.util.stream.Collectors;


public final class ImageOperations {
	private static final Logger logger = LoggerFactory.getLogger(ImageOperations.class);
	private static final String[] imageKeywords = {"gallery", "slider", "carousel", "photo", "image"};
	private static final List<String> keywordsToAvoid = Arrays.asList("logo", "gif", "banner", "fuel", "sticker",
			"mileage", "button", "credit", "trade", "background", "placeholder", "holder", "close", "document", "comingsoon",
			"coming-soon", "clicktoenlarge", "notfound", "not-found", "default", "graph", "chart", "back", "stock", "noimage",
			"wysiwyg", "micro10", "review", "no-image");
	private static final String[] validImageExtensions = {".jpg", ".jpeg", ".JPG", ".JPEG"};

	public static String getMainImageUrl(Document document, String listingId, String uniqueIdentifier, boolean chooseFirst) {
		Elements imageElements = new Elements();
		List<String> imageUrls;

		if(StringUtils.isNotEmpty(listingId)) {
			imageElements.addAll(document.getElementsByAttributeValueContaining("src", listingId).stream()
					.filter(e -> StringUtils.equalsIgnoreCase(e.tagName(), "img")).collect(Collectors.toList()));
		}
		if(StringUtils.isNotEmpty(uniqueIdentifier) && !StringUtils.equalsIgnoreCase(listingId, uniqueIdentifier)) {
			imageElements.addAll(document.getElementsByAttributeValueContaining("src", uniqueIdentifier).stream()
					.filter(e -> StringUtils.equalsIgnoreCase(e.tagName(), "img")).collect(Collectors.toList()));
		}
		if(!imageElements.isEmpty()) {
			imageUrls = imageElements.stream()
					.filter(e -> keywordsToAvoid.stream().noneMatch(s ->
							StringUtils.containsIgnoreCase(e.outerHtml(), s)))
					.filter(e -> e.hasAttr("src"))
					.map(e -> e.absUrl("src"))
					.filter(UrlValidator.getInstance()::isValid)
					.filter(s -> StringUtils.containsAny(s, validImageExtensions))
					.distinct()
					.collect(Collectors.toList());
		} else {
			for(String keyword : imageKeywords) {
				imageElements.addAll(document.getElementsByAttributeValueContaining("class", keyword));
				imageElements.addAll(document.getElementsByAttributeValueContaining("id", keyword));
			}
			imageUrls = imageElements.stream()
					.distinct()
					.flatMap(e -> e.getElementsByTag("img").stream())
					.filter(e -> keywordsToAvoid.stream().noneMatch(s ->
							StringUtils.containsIgnoreCase(e.outerHtml(), s)))
					.filter(e -> e.hasAttr("src"))
					.map(e -> e.absUrl("src"))
					.filter(UrlValidator.getInstance()::isValid)
					.filter(s -> StringUtils.containsAny(s, validImageExtensions))
					.distinct()
					.collect(Collectors.toList());
		}
		if(!chooseFirst && imageUrls.size() > 5) {
			Map<String, Double> distanceMap = new HashMap<>();
			double totalAverage = 0;
			for(int x = 0; x < imageUrls.size(); x++) {
				String imageToCalculate = imageUrls.get(x);
				int distance = 0;
				for(int y = 0; y < imageUrls.size(); y++) {
					if(y == x) {
						continue;
					}
					distance += LevenshteinDistance.getDefaultInstance().apply(imageToCalculate, imageUrls.get(y));
				}
				double average = (double)distance / imageUrls.size();
				totalAverage += average;
				distanceMap.putIfAbsent(imageToCalculate, average);
			}
			final int MAX_AVERAGE_DISTANCE = 10;
			double averageDistance = totalAverage / imageUrls.size();
			// System.out.println("average distance: " + averageDistance);

			distanceMap.forEach((k, v) -> {
				if((v > averageDistance && v - averageDistance > MAX_AVERAGE_DISTANCE)
						|| v < averageDistance && averageDistance - v > MAX_AVERAGE_DISTANCE) {
					imageUrls.remove(k);
				}
			});
		}
		if(imageUrls.isEmpty()) {
			return null;
		}
		// get the longest? idk improve this shit
		if(!chooseFirst) {
			imageUrls.sort(Comparator.comparingInt(String::length).reversed());
		}
		String image = imageUrls.get(0);

		// if the picture contains width or height query params, increase them to 500
		URL imageUrlObj;
		try {
			imageUrlObj = new URL(image);
		} catch(MalformedURLException e) {
			logger.debug(ConsoleColors.red("Malformed image URL [{}]"), image);
			return null;
		}
		int widthBegin = -1, widthEnd = -1, heightBegin = -1, heightEnd = -1;
		if(StringUtils.isNotEmpty(imageUrlObj.getQuery())) {
			for(String pair : imageUrlObj.getQuery().split("&")) {
				int x = pair.indexOf("=");
				if(x == -1) {
					continue;
				}
				String param;
				String value;
				try {
					param = URLDecoder.decode(pair.substring(0, x), "UTF-8");
					value = URLDecoder.decode(pair.substring(x + 1), "UTF-8");
				} catch(Exception e) {
					return image;
				}
				if((param.equalsIgnoreCase("w") || param.equalsIgnoreCase("width"))
						&& NumberUtils.isDigits(value)) {
					String full = param + "=" + value;
					widthBegin = image.indexOf(full) + param.length() + 1;
					widthEnd = image.indexOf(full) + full.length();
				} else if((param.equalsIgnoreCase("h") || param.equalsIgnoreCase("height"))
						&& NumberUtils.isDigits(value)) {
					String full = param + "=" + value;
					heightBegin = image.indexOf(full) + param.length() + 1;
					heightEnd = image.indexOf(full) + full.length();
				}
			}
		}
		if(widthBegin != -1) {
			image = replaceSizeParam(image, widthBegin, widthEnd);
		}
		if(heightBegin != -1) {
			image = replaceSizeParam(image, heightBegin, heightEnd);
		}
		return image;
	}

	private static String replaceSizeParam(String image, int begin, int end) {
		String sizeStr = image.substring(begin, end);
		if(NumberUtils.isDigits(sizeStr)) {
			Integer size = Integer.parseInt(sizeStr);
			if(size < 500) {
				image = image.substring(0, begin) + 500 + image.substring(end);
			}
		}
		return image;
	}
}
