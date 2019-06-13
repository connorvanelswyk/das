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

package com.findupon.commons.bot.automotive;

import com.findupon.commons.building.AddressOperations;
import com.findupon.commons.building.AutoParsingOperations;
import com.findupon.commons.entity.building.State;
import com.findupon.commons.entity.product.BuiltProduct;
import com.findupon.commons.entity.product.automotive.Automobile;
import com.findupon.commons.utilities.JsoupUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.validator.routines.UrlValidator;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


public class TrueCarBot extends ListingAutomobileBot {

	@Override
	public Set<String> retrieveBaseUrls() {
		String[] makesTheyNoHave = {"bugatti", "daewoo", "delorean", "eagle", "geo", "spyker", "pagani", "karma"};
		String rootUrl = getDataSource().getUrl() + "used-cars-for-sale/listings/";

		attributeMatcher.getFullAttributeMap().forEach((k, v) -> {
			if(Arrays.stream(makesTheyNoHave).noneMatch(m -> StringUtils.equalsIgnoreCase(v.getAttribute(), m))) {
				String make = StringUtils.replace(StringUtils.lowerCase(v.getAttribute()), " ", "-");
				String makeUrl = rootUrl + make + "/";

				Document makeDoc = download(makeUrl);
				if(makeDoc == null) {
					logger.warn(logPre() + "Null make doc from [{}]", makeUrl);
				} else {
					if(getPageResultSize(makeDoc, makeUrl) > 3500) { // 35 per page, max of 100 per page. reduce by adding model base urls
						v.getChildren().entrySet().stream().map(Map.Entry::getValue).forEach(p -> {
							String model = StringUtils.replace(StringUtils.lowerCase(p.getAttribute()), " ", "-");
							baseUrls.add(makeUrl + model + "/");
						});
					} else {
						baseUrls.add(makeUrl);
					}
				}
			}
		});
		logger.info(logPre() + "Final base URL collection size [{}]", String.format("%,d", baseUrls.size()));
		return baseUrls;
	}

	private int getPageResultSize(Document document, String baseUrl) {
		if(document == null) {
			logger.error(logPre() + "Doc came back null getting rs [{}]", baseUrl);
			return -1;
		}
		Element rsElement = document.selectFirst("h2[class=\"light pull-left\"]");
		if(rsElement == null) {
			logger.error(logPre() + "You need to update the selector for result size element [{}]", document.location());
			return -1;
		}
		String rsText = StringUtils.lowerCase(StringUtils.trimToNull(rsElement.ownText()));
		if(rsText == null) {
			logger.error(logPre() + "Missing text for result size element [{}]", document.location());
			return -1;
		}
		String rsStr = StringUtils.trimToNull(StringUtils.remove(StringUtils.substringBetween(rsText, "of", "used"), ","));
		if(!NumberUtils.isDigits(rsStr)) {
			logger.error(logPre() + "Invalid result size element number [{}]", document.location());
			return -1;
		}
		return Integer.parseInt(rsStr);
	}

	@Override
	public void gatherProductUrls(List<String> baseUrls, long nodeId) {
		String portletSelector = "div[class=\"vehicle-card small\"]";
		String noResultsMessage = "We weren't able to find any matching results";

		indexOnlyGathering(nodeId, baseUrls, url -> abstractSerpBuilder(url, "page", portletSelector, noResultsMessage, 100, (doc, baseUrl) -> {
			if(getPageResultSize(doc, baseUrl) > 3500) { // 35 per page, 100 max pages. reduce by mileage
				Set<String> additionalUrls = new HashSet<>();
				IntStream.range(0, 10).map(x -> x * 10_000).forEach(low -> {
					int high = low + 10_000;
					if(high == 100_000) {
						high = 500_000;
					}
					additionalUrls.add(baseUrl + "?mileageHigh=" + high + "&mileageLow=" + low);
				});
				return additionalUrls;
			}
			return new HashSet<>();

		}, e -> {
			Automobile automobile = new Automobile();
			Element linkTitleAnchor = e.selectFirst("a[class=vdp-link]");
			if(linkTitleAnchor == null) {
				logger.warn(logPre() + "Missing link and title anchor");
				return null;
			}
			String title = e.select("span").stream()
					.map(Element::ownText)
					.collect(Collectors.joining(" "));

			if(!automotiveGatherer.setMakeModelTrimYear(automobile, title)) {
				logger.warn(logPre() + "Could not parse MMY from text [{}]", title);
				return null;
			}
			String autoUrl = linkTitleAnchor.absUrl("href");
			if(!UrlValidator.getInstance().isValid(autoUrl)) {
				logger.warn(logPre() + "Invalid detail link url [{}]", autoUrl);
				return null;
			}
			automobile.setUrl(autoUrl);

			Element detailList = e.selectFirst("ul[class*=vehicle-info]");
			if(detailList == null) {
				logger.warn(logPre() + "Missing detail link element on serp [{}]", url);
				return null;
			}
			for(Element li : detailList.select("li")) {
				String key = JsoupUtils.selectFirst(li, "strong").map(Element::ownText).orElse(null);
				String value = li.ownText();
				if(StringUtils.containsIgnoreCase(key, "location") && StringUtils.contains(value, ",")) {
					String city = StringUtils.substringBefore(value, ",");
					String state = StringUtils.substringAfter(value, ",");
					AddressOperations.getAddressFromCityState(city, State.valueOfAbbreviation(state))
							.ifPresent(a -> a.setAutomobileAddress(automobile));
				} else {
					AutoParsingOperations.setAttribute(automobile, key, value);
				}
			}
			if(automobile.getVin() == null) {
				logger.debug(logPre() + "No vin for automobile [{}]", automobile.getUrl());
				return null;
			}
			automobile.setListingId(automobile.getVin());
			Element priceElement = e.selectFirst("p[class=price]");
			if(priceElement == null) {
				logger.warn(logPre() + "No price element found on serp ", url);
			} else {
				automobile.setPrice(AutoParsingOperations.parsePrice(priceElement.ownText()));
			}
			Element imgElement = e.selectFirst("img[class=vehicle-thumbnail]");
			if(imgElement != null && imgElement.hasAttr("src")) {
				automobile.setMainImageUrl(imgElement.absUrl("src"));
			}
			return automobile;

		}, (doc) -> {
			Element nextButton = doc.selectFirst("span[aria-label=Next]");
			if(nextButton == null) {
				logger.error(logPre() + "Next button was not found. This should not happen. URL [{}]", doc.location());
				return false;
			}
			Element a = nextButton.parent();
			if(a == null || !StringUtils.equals(a.tagName(), "a")) {
				logger.error(logPre() + "You need to update the next page a selector. URL [{}]", doc.location());
				return false;
			}
			Element li = nextButton.parent().parent();
			if(li == null || !StringUtils.equals(li.tagName(), "li")) {
				logger.error(logPre() + "You need to update the next page li selector. URL [{}]", doc.location());
				return false;
			}
			return !li.hasClass("disabled");
		}));
	}

	@Override
	public BuiltProduct buildProduct(String url) {
		return standardBuilder(url, ((document, automobile) -> {
			JsoupUtils.selectFirst(document, "ul[class*=list-divided text-md] > li > strong")
					.map(Element::ownText)
					.map(AddressOperations::getAddressFromCityStateStr)
					.filter(Optional::isPresent)
					.ifPresent(a -> a.get().setAutomobileAddress(automobile));

			automobile.setMainImageUrl(JsoupUtils.getImageSource(document, "img[class*=img-inner]"));
		}));
	}
}
