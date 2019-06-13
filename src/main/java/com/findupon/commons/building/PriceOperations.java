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

import com.findupon.commons.entity.building.TagWeight;
import com.findupon.commons.exceptions.SoldException;
import com.findupon.commons.utilities.ConsoleColors;
import com.findupon.commons.utilities.Functions;
import com.findupon.commons.utilities.JsoupUtils;
import com.findupon.commons.utilities.RegexConstants;
import com.findupon.utilities.ContainsCollectionOwnText;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Collector;
import org.jsoup.select.Evaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;


public final class PriceOperations {
	private static final Logger logger = LoggerFactory.getLogger(PriceOperations.class);
	private static final List<String> unavailableKeywords = Arrays.asList("price unavailable", "price on request", "call for price", "contact for price");

	private static final Functions.ThrowingConsumer<String, SoldException> soldTextRecognizer = s -> {
		if(s.length() < 12 && StringUtils.containsIgnoreCase(s, "sold")) {
			throw new SoldException();
		}
	};
	private static final Functions.ThrowingConsumer<Element, SoldException> soldImageElementRecognizer = image -> {
		if(image.hasAttr("src")) {
			String src = image.attr("src");
			if(AttributeOperations.containsLoneAttribute(src, "sold")) {
				throw new SoldException();
			}
		}
	};

	public static String getPrice(Element element, Consumer<List<String>> priceScrubber) throws SoldException {
		List<Element> basicCheckElements = element.getElementsByAttributeValueContaining("class", "price");
		basicCheckElements.addAll(element.getElementsByAttributeValueContaining("id", "price"));
		basicCheckElements.addAll(element.getElementsByTag("h1"));
		basicCheckElements.addAll(element.getElementsByTag("h2"));

		// *attempt* to find a sold keyword
		List<Element> potentialSoldElements = element.getElementsByAttributeValueContaining("class", "sold");
		potentialSoldElements.addAll(element.getElementsByAttributeValueContaining("id", "sold"));
		potentialSoldElements.addAll(basicCheckElements);
		potentialSoldElements.stream()
				.map(JsoupUtils.defaultFilteringTextMapper)
				.filter(StringUtils::isNotEmpty)
				.forEach(soldTextRecognizer::accept);

		element.getElementsByTag("img").forEach(soldImageElementRecognizer::accept);

		// check for any obvious information pointing to unavailable prices before continuing
		for(Element basicPriceElement : basicCheckElements) {
			if(!Collector.collect(new ContainsCollectionOwnText(unavailableKeywords), basicPriceElement).isEmpty()) {
				logger.debug("Found unavailable price keyword in text [{}]", basicPriceElement.text());
				return null;
			}
		}

		// remove spaces between $ and a numeric value
		String html = JsoupUtils.stripTags(element.html(), TagWeight.formattingTags);
		html = html.replaceAll("(?<=\\$)\\s+?(?=\\d)", ""); // replaces any spaces between $ and digits
		html = html.replace("*", " ");
		Document priceCleanedDoc = Jsoup.parse(html);

		List<String> priceValues = getPriceValues(priceCleanedDoc, true);
		priceScrubber.accept(priceValues);

		if(priceValues.isEmpty()) {
			priceValues = getPriceValues(priceCleanedDoc, false);
			priceScrubber.accept(priceValues);
		}

		if(priceValues.isEmpty()) {
			// try with a different tactic, the $ is probably in a different tag so time to strip
			priceValues = JsoupUtils.streamText(priceCleanedDoc)
					.filter(s -> s.contains("$"))
					.filter(s -> s.matches(RegexConstants.PRICE_MATCH))
					.flatMap(s -> priceCleanedDoc.getElementsContainingOwnText(s).stream()) // need the elements for validation
					.map(e1 -> JsoupUtils.dualFilteringTextMapper.apply(e1,
							s -> s.length() < 32,
							e2 -> !"a".equals(e2.tagName())))
					.flatMap(s -> Arrays.stream(s.split(" "))) // back to getting the actual prices post-validation
					.filter(s -> s.matches(RegexConstants.PRICE_MATCH))
					.map(PriceOperations::priceStringCleaner)
					.collect(Collectors.toList());
			priceScrubber.accept(priceValues);
		}

		// maybe the price is unavailable or on request, maybe this method is shite - who knows?
		if(priceValues.isEmpty()) {
			logger.trace("[ProductBuilder] - Could not determine price");
			return null;
		}

		String lowestPriceStr = AttributeOperations.mostFrequentValue(priceValues, values -> {
			if(values.isEmpty()) {
				return null;
			}
			Double lowestPrice = null;
			List<Double> maxValues = values.stream()
					.mapToDouble(Double::parseDouble)
					.boxed()
					.sorted(Collections.reverseOrder())
					.collect(Collectors.toList());

			logger.trace("Finding lowest acceptable price between tied values: [{}]",
					maxValues.stream().map(Object::toString).map(ConsoleColors::green).collect(Collectors.joining(", ")));

			for(int x = 0; x < maxValues.size(); x++) {
				double currentValue = maxValues.get(x);
				double acceptedRange = currentValue * 0.70d;

				if(x + 1 < maxValues.size()) {
					double nextValue = maxValues.get(x + 1);
					if(nextValue <= acceptedRange) {
						logger.trace("Min price found: [{}]", currentValue);
						lowestPrice = currentValue;
						break;
					} else if(x + 1 == maxValues.size() - 1) {
						// last iteration and the range is still valid
						logger.trace("Min price (last iteration) found: [{}]", nextValue);
						lowestPrice = nextValue;
					}
				}
			}
			if(lowestPrice == null) {
				return null;
			} else {
				String priceStr = String.valueOf(lowestPrice);
				return priceStr.contains(".") ? priceStr.substring(0, priceStr.indexOf(".")) : priceStr;
			}
		});
		if(lowestPriceStr == null) {
			return null;
		}
		return lowestPriceStr;
	}

	private static List<String> getPriceValues(Document document, boolean filterMsrp) {
		return document.getElementsContainingOwnText("$").stream()
				.map(e1 -> JsoupUtils.dualFilteringTextMapper.apply(e1,
						s -> s.matches(".*\\d+.*"),
						e2 -> e2.parents()
								.stream()
								.filter(p -> !TagWeight.largeTagsToAvoid.contains(p.tagName()))
								.anyMatch(p -> {
									String outerHtml = p.outerHtml();
									return StringUtils.containsIgnoreCase(outerHtml, "price")
											&& (!filterMsrp || !StringUtils.containsIgnoreCase(outerHtml, "msrp"));
								})))
				.filter(s -> s.length() > 0 && s.length() < 32)
				.flatMap(s -> Arrays.stream(s.split(" "))) // multiple prices can appear in a single text
				.filter(s -> s.contains("$")) // avoid unnecessary, expensive regex with a quick contains
				.filter(s -> s.matches(RegexConstants.PRICE_MATCH))
				.map(PriceOperations::priceStringCleaner)
				.collect(Collectors.toList());
	}

	public static String priceStringCleaner(String price) {
		if(StringUtils.isBlank(price)) {
			return StringUtils.EMPTY;
		}
		price = price.contains(".") ? price.substring(0, price.indexOf(".")) : price;
		price = price.replace("$", "").replace(",", "");
		price = StringUtils.trimToEmpty(price);
		return price;
	}

	public static boolean inRange(BigDecimal price, int minInclusive, int maxInclusive) {
		if(price == null || minInclusive > maxInclusive) {
			return false;
		}
		BigDecimal minPrice = new BigDecimal(minInclusive);
		BigDecimal maxPrice = new BigDecimal(maxInclusive);
		return price.compareTo(minPrice) >= 0 && price.compareTo(maxPrice) <= 0;
	}
}
