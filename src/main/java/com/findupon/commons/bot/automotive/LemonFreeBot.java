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
import com.findupon.commons.searchparty.ScoutServices;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.validator.routines.UrlValidator;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;


public class LemonFreeBot extends ListingAutomobileBot {

	@Override
	public Set<String> retrieveBaseUrls() {
		Set<String> makes = attributeMatcher.getFullAttributeMap().entrySet().stream()
				.map(e -> e.getValue().getAttribute())
				.collect(Collectors.toSet());
		String rootUrl = getDataSource().getUrl() + "cars/";
		String[] makesTheyNoHave = {"Karma", "Ariel", "Pagani", "Koenigsegg", "Saleen", "Fisker"};
		State.stateAbbreviations.forEach(abbr ->
				makes.stream()
						.filter(s -> Arrays.stream(makesTheyNoHave).noneMatch(s::equalsIgnoreCase))
						.map(s -> s.replace("-", "=").replace(" ", "-"))
						.map(s -> rootUrl + "used-for-sale-" + s + "/") // "used-for-sale-STATE" == used & new (vehicle_condition-Used is to get actual condition)
						.map(s -> s + "state-" + abbr + "/has_image-no/page-1")
						.forEach(baseUrls::add));
		logger.info("[LemonFreeBot] - Base URL collection size [{}]", String.format("%,d", baseUrls.size()));
		return baseUrls;
	}

	@Override
	public void gatherProductUrls(List<String> baseUrls, long nodeId) {
		indexOnlyGathering(nodeId, baseUrls, this::gatherAndBuildFromSerp);
	}

	private int gatherAndBuildFromSerp(String baseUrl) {
		Document document;
		String currentUrl = baseUrl;
		int pageNum = 1, builtAutomobiles = 0;

		do {
			logger.debug(logPre() + "Building all automobiles from serp at [{}]", currentUrl);
			document = download(currentUrl);
			if(sleep()) {
				return builtAutomobiles;
			}
			if(document == null) {
				logger.warn(logPre() + "Null document returned from serp URL [{}]", currentUrl);
				break;
			}
			// check if over 1000 results are returned, and if so, narrow the search by body style
			Element resultSizeElement = document.selectFirst("span[id=srp_title_of]");
			if(!StringUtils.containsIgnoreCase(currentUrl, "/body-") && resultSizeElement != null && resultSizeElement.hasText()) {
				String resultSizeStr = StringUtils.remove(resultSizeElement.text(), ",");
				if(NumberUtils.isDigits(resultSizeStr)) {
					int resultSize = Integer.parseInt(resultSizeStr);
					if(resultSize >= 1000) {
						List<String> bodyStyles = Arrays.asList("SUV", "Sedan", "Truck", "Coupe", "Minivan", "Wagon", "Convertible", "Hatchback", "Van");
						for(String bodyStyle : bodyStyles) {
							gatherAndBuildFromSerp(currentUrl + "/body-" + bodyStyle);
						}
						break;
					}
				}
			}
			for(Element serpElement : document.select("a[class*=srp_result]")) {
				try {
					String vin = serpElement.attr("ll-vin");
					if(!AutoParsingOperations.vinRecognizer().test(vin)) {
						logger.debug(logPre() + "Invalid VIN [{}]", vin);
						continue;
					}
					vin = vin.toUpperCase(Locale.ENGLISH);
					String autoUrl = serpElement.attr("abs:href");
					if(ScoutServices.getUrlFromString(autoUrl, false) == null) {
						logger.debug(logPre() + "Invalid detail page URL [{}]", autoUrl);
						continue;
					}
					if(autoUrl.contains("?")) {
						autoUrl = autoUrl.substring(0, autoUrl.lastIndexOf("?"));
					}
					Automobile automobile = new Automobile(autoUrl);
					automobile.setVin(vin);
					automobile.setListingId(vin);

					Element imageElement = serpElement.selectFirst("img[class*=vimg]");
					String imgUrl;
					if(imageElement != null && UrlValidator.getInstance().isValid(imgUrl = imageElement.attr("data-original"))) {
						automobile.setMainImageUrl(imgUrl);
					}
					Element titleAndMoreElement = serpElement.selectFirst("div[class=col_m]");
					if(titleAndMoreElement == null) {
						logger.warn(logPre() + "Missing title and more wrapper needed for parsing");
						continue;
					}
					Element titleElement = titleAndMoreElement.selectFirst("div[class=h4]");
					Element milesLocElement = titleAndMoreElement.selectFirst("div > div:not([class=h4])");
					if(titleElement == null || milesLocElement == null || !titleElement.hasText() || !milesLocElement.hasText()) {
						logger.warn(logPre() + "Missing title or miles and location elements or text needed for parsing");
						continue;
					}
					String titleText = StringUtils.trimToNull(titleElement.ownText());
					if(titleText == null) {
						logger.warn(logPre() + "Missing title text");
						continue;
					}
					if(!automotiveGatherer.setMakeModelTrimYear(automobile, titleText)) {
						logger.warn(logPre() + "Could not determine MMY from text [{}]", titleText);
						continue;
					}
					String milesLocText = StringUtils.trimToNull(milesLocElement.ownText());
					if(StringUtils.containsIgnoreCase(milesLocText, " miles")) {
						String milesStr = milesLocText.substring(0, StringUtils.indexOfIgnoreCase(milesLocText, " miles"));
						milesStr = StringUtils.remove(milesStr, ",");
						if(NumberUtils.isDigits(milesStr)) {
							int mileage = Integer.parseInt(milesStr);
							if(mileage >= 0 && mileage <= 500_000) {
								automobile.setMileage(mileage);
							}
						}
					} else if(automobile.getYear() != null && automobile.getYear() >= 2018) {
						automobile.setMileage(0);
					}
					if(StringUtils.containsIgnoreCase(milesLocText, "in ")) {
						String locationStr = milesLocText.substring(StringUtils.indexOfIgnoreCase(milesLocText, "in ") + "in ".length());
						if(locationStr.contains(", ")) {
							String city = locationStr.substring(0, locationStr.indexOf(","));
							State state = State.valueOfAbbreviation(locationStr.substring(locationStr.indexOf(",") + 1).trim());
							AddressOperations.getAddressFromCityState(city, state).ifPresent(a -> a.setAutomobileAddress(automobile));
						}
					}
					Element priceElement = serpElement.selectFirst("span[class=current-price]");
					if(priceElement != null && priceElement.hasText()) {
						String priceStr = priceElement.ownText();
						if(StringUtils.contains(priceStr, "$")) {
							automobile.setPrice(AutoParsingOperations.parsePrice(priceStr));
						}
					}
					validateSetMetaAndAdd(automobile);
					builtAutomobiles++;

				} catch(Exception e) {
					logger.error(logPre() + "Error building automobile from serp URL [{}]", currentUrl, e);
				}
			}
			if(pageNum > 67) {
				logger.error(logPre() + "Page num over 67, aborting. This probably indicates and indexer gone rogue. Last URL [{}] Base URL [{}]", currentUrl, baseUrl);
				break;
			}
			currentUrl = currentUrl.replaceAll("/page-\\d+", "/page-" + ++pageNum);
		}
		while(document.selectFirst("a[aria-label=Next]") != null && !StringUtils.containsIgnoreCase(document.html(), "we do not have results"));
		return builtAutomobiles;
	}

	@Override
	public BuiltProduct buildProduct(String url) {
		return standardBuilder(url);
	}
}
