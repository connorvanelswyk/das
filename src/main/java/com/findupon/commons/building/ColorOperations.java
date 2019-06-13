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

import com.findupon.commons.entity.product.attribute.ExteriorColor;
import com.findupon.commons.entity.product.attribute.InteriorColor;
import com.findupon.commons.searchparty.ScoutServices;
import com.findupon.commons.utilities.JsoupUtils;
import com.findupon.utilities.ContainsCollectionOwnText;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Collector;
import org.jsoup.select.Evaluator;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


public final class ColorOperations {

	private static final List<String> extMetaMatches = Arrays.asList("exterior color", "exterior", "color", "ext. color", "ext color");
	private static final List<String> extMetaAvoid = Arrays.asList("interior", "int color", "int. color", "kit", "caliper", "wheel", "tire",
			"width", "height", "length", "grill", "exhaust", "mirror");
	private static final List<String> extTrimAround = Collections.singletonList("roof");
	private static final List<String> extMatches = Arrays.stream(ExteriorColor.values())
			.flatMap(e -> e.getAllowedMatches().stream()).collect(Collectors.toList());

	private static final List<String> intMetaMatches = Arrays.asList("interior", "interior color", "int. color", "int color");
	private static final List<String> intMetaAvoid = Arrays.asList("exterior", "ext", "ext.", "kit", "caliper", "wheel", "tire",
			"width", "height", "length", "grill", "exhaust", "mirror", "accent", "lighting");
	private static final List<String> intMatches = Arrays.stream(InteriorColor.values())
			.flatMap(e -> e.getAllowedMatches().stream()).collect(Collectors.toList());


	public static ExteriorColor getExteriorColor(Document document) {
		List<ExteriorColor> foundExtColors = new ArrayList<>();
		List<Element> extColorElements = Collector.collect(new ContainsCollectionOwnText(extMetaMatches), document);
		String extColorBlob = StringUtils.lowerCase(extColorElements.stream()
				.map(e -> parentColorMapper(extMatches).apply(e))
				.map(JsoupUtils.defaultFilteringTextMapper)
				.map(StringUtils::trimToEmpty)
				.filter(StringUtils::isNotEmpty)
				.map(ScoutServices::pureTextNormalizer)
				.filter(s -> s.length() > 2 && s.length() < 100)
				.filter(s -> extMetaAvoid.stream().noneMatch(a -> StringUtils.containsIgnoreCase(s, a)))
				.filter(s -> extMatches.stream().anyMatch(c -> AttributeOperations.containsLoneAttribute(s, c)))
				.collect(Collectors.joining(" ")), Locale.ENGLISH);

		if(StringUtils.isNotBlank(extColorBlob)) {
			for(String trimAround : extTrimAround) {
				int ix = extColorBlob.indexOf(trimAround);
				if(ix != -1) {
					String before = extColorBlob.substring(0, ix).trim();
					String after = extColorBlob.substring(ix + trimAround.length()).trim();
					int bix = before.lastIndexOf(" ");
					if(bix != -1) {
						before = before.substring(0, bix);
					}
					int aix = after.indexOf(" ");
					if(aix != -1) {
						after = after.substring(aix);
					}
					extColorBlob = before + " " + after;
				}
			}
			foundExtColors = findExteriorColors(extColorBlob);
		} else {
			// exterior-interior most likely grouped in same parent which would have been filtered out, try a more key-value-esque approach
			extColorElements = extColorElements.stream()
					.flatMap(e -> e.parent().children().stream())
					.filter(JsoupUtils.defaultTextQualityGate)
					.filter(e -> StringUtils.trimToEmpty(e.text()).length() > 2)
					.collect(Collectors.toList());
			for(int x = 0; x < extColorElements.size(); x++) {
				String keyText = ScoutServices.pureTextNormalizer(extColorElements.get(x).text(), true);
				if(keyText != null
						&& extMetaMatches.stream().anyMatch(m -> StringUtils.containsIgnoreCase(keyText, m))
						&& extMetaAvoid.stream().noneMatch(m -> StringUtils.containsIgnoreCase(keyText, m))) {
					// 1. check for color in same text
					List<ExteriorColor> keyTextColors = findExteriorColors(keyText);
					if(!keyTextColors.isEmpty()) {
						foundExtColors.addAll(keyTextColors);
					} else {
						// 2. if not in the same text, check the next element
						if(x + 1 < extColorElements.size()) {
							String valueText = ScoutServices.pureTextNormalizer(extColorElements.get(x + 1).text(), true);
							if(valueText != null && extMetaAvoid.stream().noneMatch(m -> StringUtils.containsIgnoreCase(valueText, m))) {
								foundExtColors.addAll(findExteriorColors(valueText));
							}
						}
					}
				}
			}
		}
		return AttributeOperations.mostFrequentValue(foundExtColors);
	}

	public static InteriorColor getInteriorColor(Document document) {
		List<InteriorColor> foundIntColors = new ArrayList<>();
		List<Element> intColorElements = Collector.collect(new ContainsCollectionOwnText(intMatches), document);
		String intColorBlob = StringUtils.lowerCase(intColorElements.stream()
				.map(e -> parentColorMapper(intMatches).apply(e))
				.map(JsoupUtils.defaultFilteringTextMapper)
				.filter(StringUtils::isNotBlank)
				.map(ScoutServices::pureTextNormalizer)
				.filter(s -> s.length() > 2 && s.length() < 100)
				.map(s -> {
					String lower = s.toLowerCase(Locale.ENGLISH);
					int i = lower.indexOf("stitching");
					if(i >= 0) {
						// e.g. "with red stitching"
						String before = StringUtils.trimToEmpty(StringUtils.substring(lower, 0, i));
						if(before.contains(" ")) {
							before = StringUtils.substringAfterLast(before, " ");
							ExteriorColor temp = ExteriorColor.of(before);
							if(temp != null) {
								return StringUtils.substring(lower, 0, StringUtils.lastIndexOf(lower, before));
							}
						}
					}
					return s;
				})
				.map(StringUtils::trimToEmpty)
				.filter(StringUtils::isNotEmpty)
				.filter(s -> intMetaAvoid.stream().noneMatch(a -> StringUtils.containsIgnoreCase(s, a)))
				.filter(s -> intMatches.stream().anyMatch(c -> AttributeOperations.containsLoneAttribute(s, c)))
				.collect(Collectors.joining(" ")), Locale.ENGLISH);

		if(StringUtils.isNotBlank(intColorBlob)) {
			foundIntColors = findInteriorColors(intColorBlob);
		} else {
			intColorElements = intColorElements.stream()
					.flatMap(e -> e.parent().children().stream())
					.limit(32)
					.filter(JsoupUtils.defaultTextQualityGate)
					.filter(e -> StringUtils.trimToEmpty(e.text()).length() > 2)
					.collect(Collectors.toList());
			for(int x = 0; x < intColorElements.size(); x++) {
				String keyText = ScoutServices.pureTextNormalizer(intColorElements.get(x).text(), true);
				if(keyText != null
						&& intMetaMatches.stream().anyMatch(m -> StringUtils.containsIgnoreCase(keyText, m))
						&& intMetaAvoid.stream().noneMatch(m -> StringUtils.containsIgnoreCase(keyText, m))) {
					List<InteriorColor> keyTextColors = findInteriorColors(keyText);
					if(!keyTextColors.isEmpty()) {
						foundIntColors.addAll(keyTextColors);
					} else {
						if(x + 1 < intColorElements.size()) {
							String valueText = ScoutServices.pureTextNormalizer(intColorElements.get(x + 1).text(), true);
							if(valueText != null && intMetaAvoid.stream().noneMatch(m -> StringUtils.containsIgnoreCase(valueText, m))) {
								foundIntColors.addAll(findInteriorColors(valueText));
							}
						}
					}
				}
			}
		}
		return AttributeOperations.mostFrequentValue(foundIntColors);
	}

	// yes, this should be recursive
	private static Function<? super Element, ? extends Element> parentColorMapper(List<String> matches) {
		return e -> {
			Element p0 = e.parent();
			if(p0 == null) {
				return e;
			}
			String p0t = p0.text();
			if(matches.stream().anyMatch(c -> AttributeOperations.containsLoneAttribute(p0t, c))) {
				return p0;
			}
			Element p1 = p0.parent();
			if(p1 == null) {
				return e;
			}
			String p1t = p1.text();
			if(matches.stream().anyMatch(c -> AttributeOperations.containsLoneAttribute(p1t, c))) {
				return p1;
			}
			return e;
		};
	}

	private static List<ExteriorColor> findExteriorColors(String text) {
		List<ExteriorColor> foundExtColors = new ArrayList<>();
		for(ExteriorColor color : ExteriorColor.values()) {
			for(String colorStr : color.getAllowedMatches()) {
				for(String segment : text.split(" ")) {
					if(StringUtils.equalsIgnoreCase(segment, colorStr)) {
						foundExtColors.add(color);
					}
				}
			}
		}
		return foundExtColors;
	}

	private static List<InteriorColor> findInteriorColors(String text) {
		List<InteriorColor> foundIntColors = new ArrayList<>();
		for(InteriorColor color : InteriorColor.values()) {
			for(String colorStr : color.getAllowedMatches()) {
				for(String segment : text.split(" ")) {
					if(StringUtils.equalsIgnoreCase(segment, colorStr)) {
						foundIntColors.add(color);
					}
				}
			}
		}
		return foundIntColors;
	}
}
