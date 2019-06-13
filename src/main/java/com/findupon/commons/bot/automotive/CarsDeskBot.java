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
import com.findupon.commons.entity.product.attribute.Body;
import com.findupon.commons.entity.product.attribute.Drivetrain;
import com.findupon.commons.entity.product.attribute.Transmission;
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
import java.util.*;


public class CarsDeskBot extends ListingAutomobileBot {

	@Override
	public Set<String> retrieveBaseUrls() {
		baseUrls.addAll(SitemapCollector.collect(getDataSource(), "/sitemap/cars1.xml", s -> StringUtils.contains(s, "-in-")));
		return baseUrls;
	}

	@Override
	public void gatherProductUrls(List<String> baseUrls, long nodeId) {
		indexOnlyGathering(nodeId, baseUrls, this::gatherAndBuildFromSerp);
	}

	@Override
	public BuiltProduct buildProduct(String url) {
		logger.error(logPre() + "Stop calling this method it's not implemented. This is an index_only data source.");
		return null;
	}

	private int gatherAndBuildFromSerp(String baseUrl) {

		Document document = download(baseUrl);
		if(document == null || StringUtils.containsIgnoreCase(document.html(), "No local results match your search")) {
			logger.debug(logPre() + "Null Document from URL [{}]", baseUrl);
			return 0;
		}

		Set<String> urls = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

		for(Element makeElements : document.selectFirst("div[class=links-holder]").select("a[class=fix-wd]")) {
			if(makeElements.hasAttr("href")) {
				String href = makeElements.absUrl("href");
				Document modelDocument = download(href);
				if(modelDocument == null) {
					logger.warn(logPre() + "Null document at [{}]", href);
					continue;
				}
				if(StringUtils.containsIgnoreCase(modelDocument.html(), "No local results match your search")) {
					logger.debug(logPre() + "No listings at [{}]", href);
					continue;
				}
				for(Element yearElements : modelDocument.selectFirst("div[class=links-holder]").select("a[class*=fix-wd]")) {
					String yearLink = yearElements.absUrl("href");
					urls.add(yearLink);
				}
			}
		}


		int builtAutomobiles = 0;

		for(String url : urls) {
			int pageNum = 1;
			int count = 0;
			do {
				String currentUrl = url + "/Page-" + pageNum++;
				Document serpDoc = download(currentUrl);
				if(serpDoc == null || StringUtils.containsIgnoreCase(serpDoc.html(), "No local results match your search")) {
					logger.debug(logPre() + "Built [{}] automobiles in url [{}]", count, currentUrl);
					logger.debug(logPre() + "Null Document from URL or no listings [{}]", currentUrl);
					break;
				}

				Elements carContainers = document.select("div[class*=\"b-row car-list\"]");

				for(Element carContainer : carContainers) {
					try {
						Automobile automobile;
						Element linkAndTitleAnchor = carContainer.selectFirst("div[class=\"carTitle\"] > a");
						if(linkAndTitleAnchor == null || !linkAndTitleAnchor.hasText()) {
							logger.debug(logPre() + "Link and title anchor came back null or missing text");
							continue;
						}
						String carUrl = linkAndTitleAnchor.absUrl("href");
						if(UrlValidator.getInstance().isValid(carUrl)) {
							automobile = new Automobile(carUrl);
						} else {
							logger.debug(logPre() + "Invalid detail page URL [{}]", carUrl);
							continue;
						}

						Element title = carContainer.selectFirst("h2[class=list-title]");
						if(title == null) {
							logger.debug(logPre() + "No title text [{}]", carUrl);
							continue;
						}

						String titleText = ScoutServices.normalize(title.ownText());

						if(!automotiveGatherer.setMakeModelTrimYear(automobile, titleText)) {
							logger.debug(logPre() + "Could not parse MMY from title text [{}]", titleText);
							continue;
						}

						Arrays.stream(carUrl.split("-")).filter(AutoParsingOperations.vinRecognizer()).findFirst().ifPresent(s -> {
							s = s.toUpperCase(Locale.ENGLISH);
							automobile.setVin(s);
							automobile.setListingId(s);
						});

						automobile.setPrice(
								JsoupUtils.selectFirst(carContainer, "a[class*=carMD]")
										.map(JsoupUtils.defaultFilteringTextMapper)
										.map(s -> s.replaceAll("[^0-9]", ""))
										.filter(NumberUtils::isDigits)
										.map(BigDecimal::new)
										.orElse(null)
						);

						automobile.setMainImageUrl(
								JsoupUtils.selectFirst(carContainer, "div[class*=car-list-img] > a > img")
										.map(e -> e.absUrl("src"))
										.filter(UrlValidator.getInstance()::isValid)
										.orElse(null)
						);

						automobile.setMileage(
								carContainer.select("div[class=col-xs-12]").stream()
										.map(Element::ownText)
										.filter(s -> !StringUtils.containsIgnoreCase(s, "miles"))
										.map(AutoParsingOperations::parseMileage)
										.findFirst()
										.orElse(null)
						);

						carContainer.select("ul[class=\"specs-list\"] > li").stream()
								.map(JsoupUtils.defaultFilteringTextMapper)
								.forEach(s -> {
									Transmission transmission = Transmission.of(s);
									if(transmission != null) {
										automobile.setTransmission(transmission);
									}

									Drivetrain driveTrain = Drivetrain.of(s);
									if(driveTrain != null) {
										automobile.setDrivetrain(driveTrain);
									}

									Body body = Body.of(s);
									if(body != null) {
										automobile.setBody(body);
									}
								});

						validateSetMetaAndAdd(automobile);
						builtAutomobiles++;
						count++;
					} catch(Exception ex) {
						logger.error(logPre() + "Error build from serp!", ex);
					}
				}

			} while(pageNum <= 40);//25 results per page. 1000 results total
		}

		logger.debug(logPre() + "Built [{}] automobiles from base URL [{}]", builtAutomobiles, baseUrl);

		return builtAutomobiles;
	}
}
