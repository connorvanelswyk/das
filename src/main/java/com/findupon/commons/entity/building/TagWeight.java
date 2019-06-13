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

package com.findupon.commons.entity.building;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Element;

import java.util.*;


public final class TagWeight {
	public static final String[] largeTags = new String[]{"body", "html"};
	public static final String[] lowValueTags = new String[]{"head", "meta", "script", "noscript", "embed",
			"object", "param", "input", "select", "button", "style", "link", "nav"};
	public static final String[] auxiliaryTags = new String[]{"span", "strong", "br", "b", "hr"};
	public static final String[] formattingTags = new String[]{"b", "strong", "i", "em", "mark", "small", "ins", "sub", "sup"};

	public static final String[] adscititiousAttributeKeys = {"relate", "similar", "recommend", "featured", "sidebar", "menu", "footer", "recent", "alsoview"};
	public static final String[] classIdAttributeKeys = {"class", "id"};
	public static final String[] integralAttributeKeys = ArrayUtils.addAll(new String[]{"itemprop", "data-qaid"}, classIdAttributeKeys);

	public static final Set<String> largeTagsToAvoid = buildLargeTagsToAvoid();
	private static final Map<Set<String>, Double> tagWeightMap = buildTagWeightMap();
	private static final Map<String, Double> attributeValueIncreaseMap = buildAttributeValueIncreaseMap();

	public static final double DEFAULT_WEIGHT = .005;
	public static final double HEAVY_WEIGHT = .99;


	private static Map<Set<String>, Double> buildTagWeightMap() {
		Map<Set<String>, Double> weightMap = new HashMap<>();
		weightMap.put(new HashSet<>(Collections.singletonList("h1")), .02);
		weightMap.put(new HashSet<>(Collections.singletonList("h2")), .01);
		weightMap.put(new HashSet<>(Arrays.asList("h3", "h4", "h5", "h6")), .007);
		weightMap.put(new HashSet<>(Arrays.asList("ul", "li", "span")), .006);
		weightMap.put(new HashSet<>(Arrays.asList("a", "link", "img")), .001);
		weightMap.put(new HashSet<>(Arrays.asList("title", "head", "meta")), .001);
		return weightMap;
	}

	private static Map<String, Double> buildAttributeValueIncreaseMap() {
		Map<String, Double> increaseMap = new HashMap<>();
		increaseMap.put("bold", .005);
		return increaseMap;
	}

	private static Set<String> buildLargeTagsToAvoid() {
		return new HashSet<>(Arrays.asList(largeTags));
	}

	public static double getWeight(Element e) {
		String tagName = e.tagName();
		double weight = DEFAULT_WEIGHT;
		for(Map.Entry<Set<String>, Double> entry : tagWeightMap.entrySet()) {
			if(entry.getKey().contains(tagName)) {
				weight = entry.getValue();
				break;
			}
		}
		weight += adjustWeightByAttributes(e);
		return weight;
	}

	private static double adjustWeightByAttributes(Element e) {
		double increase = 0D;
		for(Attribute attr : e.attributes()) {
			for(Map.Entry<String, Double> entry : attributeValueIncreaseMap.entrySet()) {
				if(StringUtils.contains(attr.getValue(), entry.getKey())) {
					increase += entry.getValue();
				}
			}
		}
		return increase;
	}
}
