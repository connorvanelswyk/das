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
import com.findupon.commons.entity.product.attribute.ExteriorColor;
import com.findupon.commons.entity.product.automotive.Automobile;
import com.findupon.commons.searchparty.ScoutServices;
import com.findupon.commons.utilities.JsoupUtils;
import com.findupon.utilities.SitemapCollector;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.validator.routines.UrlValidator;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.findupon.commons.dao.ProductDao.productWriteThreshold;


public class EveryCarListedBot extends ListingAutomobileBot {

	@Override
	public Set<String> retrieveBaseUrls() {
		baseUrls.addAll(SitemapCollector.collectNested(getDataSource(), "sitemap-us-model-index.xml"));
		return baseUrls;
	}

	@Override
	public void gatherProductUrls(List<String> baseUrls, long nodeId) {
		indexOnlyGathering(nodeId, baseUrls, this::gatherAndBuildFromSerp);
	}

	private int gatherAndBuildFromSerp(String baseUrl) {
		Document document;
		int pageNum = 1, carContainerSize, builtAutomobiles = 0;
		baseUrl += (baseUrl.endsWith("/") ? "" : "/") + "page-" + pageNum;
		String currentUrl = baseUrl;
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
			Elements carContainers = document.select("div[class*=car-container]");
			carContainerSize = carContainers.size();
			for(Element carContainer : carContainers) {
				try {
					Automobile automobile;
					Element linkAndTitleAnchor = carContainer.selectFirst("div[class=\"sansBold font14 title\"] > a");
					if(linkAndTitleAnchor == null || !linkAndTitleAnchor.hasText()) {
						logger.warn(logPre() + "Link and title anchor came back null or missing text");
						continue;
					}
					String url = linkAndTitleAnchor.absUrl("href");
					if(UrlValidator.getInstance().isValid(url)) {
						automobile = new Automobile(url);
						automobile.setListingId(url);
					} else {
						logger.warn(logPre() + "Invalid detail page URL [{}]", url);
						continue;
					}
					if(url.contains("vin-")) {
						String vin = url.substring(url.indexOf("vin-") + "vin-".length());
						if(AutoParsingOperations.vinRecognizer().test(vin)) {
							vin = vin.toUpperCase(Locale.ENGLISH);
							automobile.setVin(vin);
						}
					}
					if(automobile.getVin() == null) {
						logger.debug(logPre() + "No vin found from automobile href [{}]", url);
						continue;
					}
					String titleText = ScoutServices.normalize(linkAndTitleAnchor.ownText());
					if(!automotiveGatherer.setMakeModelTrimYear(automobile, titleText)) {
						logger.warn(logPre() + "Could not parse MMY from title text [{}]", titleText);
						continue;
					}
					automobile.setMileage(carContainer.select("div[class*=mileage]").stream()
							.findFirst()
							.filter(Element::hasText)
							.map(Element::text)
							.map(ScoutServices::normalize)
							.map(s -> s.replaceAll("[^0-9]", ""))
							.filter(NumberUtils::isDigits)
							.map(Integer::parseInt)
							.filter(i -> i < 500_000 && i >= 0)
							.orElse(null));
					automobile.setPrice(carContainer.select("div[class*=price]").stream()
							.findFirst()
							.filter(Element::hasText)
							.map(Element::text)
							.map(ScoutServices::normalize)
							.map(s -> s.replaceAll("[^0-9]", ""))
							.filter(NumberUtils::isDigits)
							.map(BigDecimal::new)
							.orElse(null));
					String locationStr = carContainer.select("div[class*=fontblack]").stream()
							.filter(Element::hasText)
							.map(Element::text)
							.filter(s -> State.stateAbbreviations.stream().anyMatch(a -> StringUtils.containsIgnoreCase(s, a)))
							.findFirst()
							.orElse(null);
					if(locationStr != null && locationStr.contains(",")) {
						String city = locationStr.substring(0, locationStr.indexOf(","));
						State state = State.valueOfAbbreviation(locationStr.substring(locationStr.indexOf(",") + 1));
						AddressOperations.getAddressFromCityState(city, state).ifPresent(a -> a.setAutomobileAddress(automobile));
					}
					automobile.setExteriorColor(ExteriorColor.of(carContainer.getElementsContainingOwnText("Color:").stream()
							.map(Element::text)
							.findFirst()
							.map(s -> StringUtils.remove(s, "Color:"))
							.map(StringUtils::trimToNull)
							.orElse(null)));

					automobile.setMainImageUrl(carContainer.select("img[class*=srpImage]").stream()
							.findFirst()
							.map(e -> e.absUrl("src"))
							.filter(UrlValidator.getInstance()::isValid)
							.orElse(null));

					validateSetMetaAndAdd(automobile);
					builtAutomobiles++;
					if(products.size() >= productWriteThreshold) {
						logger.info(logPre() + "Persisting [{}] automobiles to the db (over threshold)", products.size());
						persistAndClear();
					}
				} catch(Exception e) {
					logger.error(logPre() + "Error building automobile from serp URL [{}]", currentUrl, e);
				}
			}
			if(pageNum > 25) {
				logger.debug(logPre() + "Page num over 25, prematurely aborting");
				break;
			}
			// if(pageNum > 100) {
			// 	logger.warn(logPre() + "Page num over 100, aborting. This probably indicates and indexer gone rogue. Last URL [{}] Base URL [{}]", currentUrl, baseUrl);
			// 	break;
			// }
			currentUrl = currentUrl.replaceAll("/page-\\d+", "/page-" + ++pageNum);
		}
		while(document.selectFirst("li[class=\"next hidden\"]") == null && carContainerSize == 30);
		return builtAutomobiles;
	}

	@Override
	public BuiltProduct buildProduct(String url) {
		return standardBuilder(url, ((document, automobile) -> {
			automobile.setMainImageUrl(JsoupUtils.getImageSource(document, "img[id=normalSizePhoto]"));
		}));
	}
}
