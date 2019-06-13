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

import com.findupon.commons.building.AutoParsingOperations;
import com.findupon.commons.entity.product.BuiltProduct;
import com.findupon.commons.entity.product.attribute.Drivetrain;
import com.findupon.commons.entity.product.attribute.ExteriorColor;
import com.findupon.commons.entity.product.attribute.Transmission;
import com.findupon.commons.entity.product.automotive.Automobile;
import com.findupon.commons.entity.product.automotive.AutomobileModel;
import com.findupon.commons.searchparty.ScoutServices;
import com.findupon.commons.utilities.JsoupUtils;
import com.findupon.utilities.PermutableAttribute;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.validator.routines.UrlValidator;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;


public class ListingAllCarsBot extends ListingAutomobileBot {

	private final int MAX_RESULT_SIZE = 1_000;

	@Override
	public Set<String> retrieveBaseUrls() {

		String root = getDataSource().getUrl() + "sitemap";//Grab the make & model URLs

		Document siteMap = download(root);

		if(siteMap == null) {
			logger.error(logPre() + "Site Map Document returned null [{}]", root);
			return baseUrls;
		}

		siteMap.select("div[class*=row well] > div > a[class=dark-link]").stream()
				.map(e -> e.absUrl("href"))
				.forEach(baseUrls::add);

		return baseUrls;
	}

	private int getPageResultSize(Document document, String baseUrl) {
		if(document == null) {
			logger.debug(logPre() + "Doc came back null getting rs [{}]", baseUrl);
			return -1;
		}

		if(document.getElementsContainingOwnText("No results found in your area.").size() > 0) {
			logger.debug(logPre() + "No results with url [{}]", baseUrl);
			return -1;
		}

		String count = StringUtils.substringBetween(document.html(), "', '", "', false);").replace(",", "");
		if(!NumberUtils.isDigits(count)) {
			logger.debug(logPre() + "Invalid result size element number [{}]", document.location());
			return -1;
		}

		return Integer.parseInt(count);
	}

	@Override
	public void gatherProductUrls(List<String> baseUrls, long nodeId) {

		for(String baseUrl : baseUrls) {
			Document doc = download(baseUrl);
			int pageSize = getPageResultSize(doc, baseUrl);

			if(pageSize > MAX_RESULT_SIZE) {
				reduceByYear(baseUrl);

			} else {
				String[] currentUrlSplit = baseUrl.split("/");
				String make = currentUrlSplit[currentUrlSplit.length - 2];
				String model = currentUrlSplit[currentUrlSplit.length - 1];
				baseUrl = StringUtils.substringBefore(baseUrl, make);
				baseUrl += "srp/?&m=" + make + "&mo=" + model;
				Document document = download(baseUrl);
				retrieveAutomobiles(document, baseUrl);
			}
		}
	}

	private void reduceByYear(String baseUrl) {
		String[] baseUrlSplit = baseUrl.split("/");
		String make = baseUrlSplit[baseUrlSplit.length - 2];
		String model = baseUrlSplit[baseUrlSplit.length - 1];
		String newBaseUrl = StringUtils.substringBefore(baseUrl, make) + "srp?&m=" + make + "&mo=" + model;

		AutomobileModel automobileModel = null;
		int makeId = attributeMatcher.getMakeId(make);

		if(makeId > 0) {
			PermutableAttribute automobileMake = attributeMatcher.getFullAttributeMap().get(makeId);
			if(automobileMake != null) {
				automobileModel = (AutomobileModel)automobileMake.getChildren().get(attributeMatcher.getModelId(makeId, model));
			}
		}

		int minYear = 1985;
		int maxYear = 2019;
		int minPrice = 1000;
		int maxPrice = 100_000;

		if(automobileModel != null) {
			minYear = automobileModel.getMinYear();
			maxYear = automobileModel.getMaxYear();
			minPrice = automobileModel.getMinPrice();
			maxPrice = automobileModel.getMaxPrice();
			logger.debug(logPre() + "Determined price range for make [{}] model [{}] min [{}] max [{}]", make, model, minPrice, maxPrice);
		} else {
			logger.warn(logPre() + "No price range found for make [{}] default min [{}] max [{}] will be used", make, minPrice, maxPrice);
		}

		for(int year = minYear; year <= maxYear; year++) {
			int range = 20_000;
			int searchMinPrice = minPrice;
			int searchMaxPrice;
			boolean rangeNextPass = true;

			Document document;

			do {
				searchMaxPrice = searchMinPrice + range;
				String priceUrl = newBaseUrl;
				priceUrl += "&pl=" + searchMinPrice;
				priceUrl += "&ph=" + searchMaxPrice;
				priceUrl += "&yl=" + year;
				priceUrl += "&yh=" + year;
				priceUrl += "&pg=1";
				priceUrl += "&ps=100";

				document = download(priceUrl);
				if(document == null) {
					logger.warn(logPre() + "Null document returned from serp URL [{}]", priceUrl);
					break;
				}
				int resultSize = getPageResultSize(document, priceUrl);
				logger.debug(logPre() + "Results [{}] from [{}]", resultSize, priceUrl);
				if(resultSize > MAX_RESULT_SIZE) {
					rangeNextPass = false;
					range /= ((resultSize + MAX_RESULT_SIZE) / MAX_RESULT_SIZE);
					if(range > 1) {
						continue;
					} else {
						// TODO: Add filter by miles
						range = 1;
					}
				} else if(resultSize <= 0) {
					searchMinPrice += range;
					range *= 2;
					continue;
				} else if(resultSize > MAX_RESULT_SIZE / 2) {
					rangeNextPass = false;
				}
				searchMinPrice += range + 1;
				if(rangeNextPass) {
					range *= Math.ceil((Math.PI + 1) / Math.log10(resultSize * 100) + 1);
				}
				rangeNextPass = true;

				//Go ahead and crawl url
				retrieveAutomobiles(document, priceUrl);

			} while(searchMaxPrice < maxPrice);
		}
	}

	private void retrieveAutomobiles(Document document, String url) {

		String portletSelector = "div[id=vehicles]";

		int currentPage = 1;
		int maxNumberPages = 40;
		int numberResults = getPageResultSize(document, url);
		if(numberResults <= 0) {
			logger.debug(logPre() + "No Results found at [{}]", url);
			return;
		}

		int automobilesParsed = 0;
		String currentUrl = url;


		do {

			Element e = document.selectFirst(portletSelector);
			Elements linkTitleAnchors = e.select("h1[class=vehicleTitle]");
			Elements carBodyElements = e.select("div[class=rowWrapper]");

			int numberCars = linkTitleAnchors == null ? 0 : linkTitleAnchors.size();

			if(numberCars == 0) {
				break;
			}

			for(int i = 0; i < numberCars; i++) {

				Automobile automobile = new Automobile();
				Element titleElement = linkTitleAnchors.get(i);
				Element bodyElement = carBodyElements.get(i);

				String title = titleElement.selectFirst("a").ownText();
				if(!automotiveGatherer.setMakeModelTrimYear(automobile, title)) {
					logger.warn(logPre() + "Could not parse MMY from text [{}]", title);
					continue;
				}

				String autoUrl = titleElement.selectFirst("a").absUrl("href");
				if(!UrlValidator.getInstance().isValid(autoUrl)) {
					logger.warn(logPre() + "Invalid detail link url [{}]", autoUrl);
					continue;
				}

				automobile.setUrl(autoUrl);

				String listingId = ScoutServices.getUniqueIdentifierFromUrl(autoUrl);

				if(listingId != null) {
					automobile.setListingId(listingId);
				}

				automobile.setPrice(JsoupUtils.priceMapper(e, "h1[class=price]"));

				Element imgElement = bodyElement.selectFirst("img[class*=img-thumbnail]");
				if(imgElement != null && imgElement.hasAttr("src")) {
					automobile.setMainImageUrl(imgElement.absUrl("src"));
				}

				Element carFaxElement = bodyElement.selectFirst("div[class=spacer-hasCarStory]");
				if(carFaxElement != null) {
					String carfaxLink = carFaxElement.nextElementSibling().absUrl("href");

					if(StringUtils.contains(carfaxLink, "&vin")) {
						String vin = StringUtils.upperCase(StringUtils.substringAfter(carfaxLink, "&vin="));
						if(AutoParsingOperations.vinRecognizer().test(vin)) {
							automobile.setVin(vin);
						}
					}
				}

				if(automobile.getVin() == null) {
					logger.debug(logPre() + "No vin found from automobile href [{}]", automobile.getUrl());
					continue;
				}

				HashMap<String, String> details = new HashMap<>();

				Arrays.stream(bodyElement.select("div[class*=srpDetail]").html().split("<br>")).forEach(
						detail -> details.put(
								StringUtils.substringBetween(detail, "<label>", "</label>")
								,
								StringUtils.substringBetween(detail, "&nbsp;", "\n")
						)
				);

				if(details.containsKey("Stock #:")) {
					automobile.setStockNumber(details.get("Stock #:"));
				}
				if(details.containsKey("Mileage:")) {
					String mileage = details.get("Mileage:").replace(",", "");
					if(NumberUtils.isDigits(mileage)) {
						automobile.setMileage(Integer.valueOf(details.get("Mileage:").replace(",", "")));
					}
				}
				if(details.containsKey("Trans:")) {
					automobile.setTransmission(Transmission.of(details.get("Trans:")));
				}
				if(details.containsKey("Drive:")) {
					automobile.setDrivetrain(Drivetrain.of(details.get("Drive:")));
				}
				if(details.containsKey("Color:")) {
					automobile.setExteriorColor(ExteriorColor.of(details.get("Color:")));
				}

				validateSetMetaAndAdd(automobile);
			}

			currentPage++;
			automobilesParsed += numberCars;

			if(automobilesParsed >= numberResults) {
				break;
			}

			if(StringUtils.containsIgnoreCase(currentUrl, "&pg=")) {
				currentUrl = StringUtils.replacePattern(currentUrl, "&pg=\\d*", "&pg=" + currentPage);
			} else {
				currentUrl += ("&pg=" + currentPage);
			}
			if(!StringUtils.containsIgnoreCase(currentUrl, "&ps=")) {
				currentUrl += "&ps=100";
			}
			document = download(currentUrl);

		} while(currentPage <= maxNumberPages);
	}

	@Override
	public BuiltProduct buildProduct(String url) {
		logger.error(logPre() + "Stop calling this method it's not implemented. This is an index_only data source.");
		return null;
	}
}
