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

package com.findupon.datasource.bot.automotive;

//
// import com.google.common.base.Stopwatch;
// import com.findupon.commons.building.AddressOperations;
// import com.findupon.commons.building.AutoParsingOperations;
// import com.findupon.commons.entity.building.State;
// import com.findupon.commons.netops.ConnectionAgent;
// import com.findupon.commons.utilities.JsoupUtils;
// import com.findupon.commons.utilities.TimeUtils;
// import org.apache.commons.lang3.StringUtils;
// import org.apache.commons.lang3.math.NumberUtils;
// import org.apache.commons.validator.routines.UrlValidator;
// import org.jsoup.nodes.Document;
// import org.jsoup.nodes.Element;
// import org.springframework.stereotype.Component;
//
// import java.math.BigDecimal;
// import java.util.ArrayList;
// import java.util.Calendar;
// import java.util.List;
// import java.util.Locale;
//
//
// @Component
// public class CarMaxProcess extends AbstractImportProcess {
// 	private final List<Automobile> automobiles = new ArrayList<>();
//
// 	@Override
// 	public void init() {
// 		Stopwatch stopwatch = Stopwatch.createStarted();
//
// 		loadAutomobiles();
// 		logger.debug("[CarMaxProcess] - Automobile loading complete, [{}] cars loaded", automobiles.size());
//
// 		setMeta(automobiles);
// 		automobiles.removeIf(a -> a.getVisitedBy() == null);
// 		logger.debug("[CarMaxProcess] - Automobile meta setting complete");
//
// 		automobileBulkDataService.bulkInsertUpdate(automobiles);
// 		logger.debug("[CarMaxProcess] - Persistence complete");
//
// 		int removed = handleDeletion();
// 		slackMessenger.sendMessageWithArgs("CarMaxProcess load process complete. %n```" +
// 						"Created/ Updated:  [%s] %n" +
// 						"Removed:           [%s] %n" +
// 						"Total time taken:  [%s] ```",
// 				String.format("%,d", automobiles.size()), String.format("%,d", removed), TimeUtils.format(stopwatch));
// 	}
//
// 	@Override
// 	protected String getSourceUrl() {
// 		return "https://www.carmax.com/";
// 	}
//
// 	private void loadAutomobiles() {
// 		final int perPage = 100;
// 		final int absoluteMaxResults = 100_000;
// 		int resultCount = absoluteMaxResults;
// 		int startIndex = 0;
//
// 		String url = "https://api.carmax.com/v1/api/vehicles" +
// 				"?Distance=all" +
// 				"&PerPage=" + perPage +
// 				"&SortKey=0" +
// 				"&StartIndex=" + startIndex +
// 				"&Zip=94027" +
// 				"&platform=carmax.com" +
// 				"&apikey=adfb3ba2-b212-411e-89e1-35adab91b600";
//
// 		while(startIndex < resultCount && startIndex < absoluteMaxResults) {
// 			Document document = ConnectionAgent.INSTANCE.xmlDownload(url, true);
// 			logger.info("[CarMaxProcess] - Current index [{}] Result count [{}] Automobiles built [{}]", startIndex, resultCount, automobiles.size());
// 			try {
// 				Thread.sleep(500L);
// 			} catch(InterruptedException e) {
// 				logger.warn("[CarMaxProcess] - Thread hath been interrupted!");
// 				Thread.currentThread().interrupt();
// 				return;
// 			}
// 			Element resultCountElement = document.selectFirst("ResultCount");
// 			if(resultCountElement == null || !resultCountElement.hasText() || !NumberUtils.isDigits(resultCountElement.text())) {
// 				logger.error("[CarMaxProcess] - Invalid result count element, aborting process. Last URL [{}]", url);
// 				return;
// 			}
// 			resultCount = Integer.parseInt(resultCountElement.text());
//
// 			List<Element> autoElements = document.select("ResultsRecordModel");
// 			if(autoElements.isEmpty()) {
// 				logger.error("[CarMaxProcess] - No result elements found, aborting process. Last URL [{}]", url);
// 				return;
// 			}
// 			for(Element autoElement : autoElements) {
// 				Automobile automobile = new Automobile();
// 				String autoUrl = JsoupUtils.firstChildOwnText(autoElement.selectFirst("Href"));
// 				if(StringUtils.containsIgnoreCase(autoUrl, "/vehicles/")) {
// 					String listingId = StringUtils.substringAfter(autoUrl, "/vehicles/");
// 					if(NumberUtils.isDigits(listingId)) {
// 						automobile.setUrl(getSourceUrl() + "car/" + listingId);
// 						automobile.setListingId(listingId);
// 					}
// 				}
// 				String titleText = StringUtils.defaultString(JsoupUtils.firstChildOwnText(autoElement.selectFirst("Description")));
// 				if(!automotiveGatherer.setMakeModelTrimYear(automobile, titleText)) {
// 					String make = StringUtils.defaultString(JsoupUtils.firstChildOwnText(autoElement.selectFirst("Make")));
// 					String model = StringUtils.defaultString(JsoupUtils.firstChildOwnText(autoElement.selectFirst("Model")));
// 					String year = StringUtils.defaultString(JsoupUtils.firstChildOwnText(autoElement.selectFirst("Year")));
// 					if(StringUtils.equalsIgnoreCase(make, "BMW") && NumberUtils.isDigits(model)) {
// 						model += "i";
// 					}
// 					titleText = year + " " + make + " " + model;
// 					if(!automotiveGatherer.setMakeModelTrimYear(automobile, titleText)) {
// 						logger.debug("Could not set MMY from text [{}] url [{}]", titleText, url);
// 						continue;
// 					}
// 				}
// 				String vin = JsoupUtils.firstChildOwnText(autoElement.selectFirst("Vin"));
// 				if(AutoParsingOperations.vinRecognizer().test(vin)) {
// 					automobile.setVin(vin.toUpperCase(Locale.ENGLISH));
// 				}
// 				String miles = JsoupUtils.firstChildOwnText(autoElement.selectFirst("Miles"));
// 				if(StringUtils.containsIgnoreCase(miles, "K")) {
// 					miles = StringUtils.replaceIgnoreCase(miles, "K", "000");
// 				}
// 				if(NumberUtils.isDigits(miles)) {
// 					automobile.setMileage(Integer.parseInt(miles));
// 				}
// 				String price = JsoupUtils.firstChildOwnText(autoElement.selectFirst("Price"));
// 				if(NumberUtils.isDigits(price)) {
// 					automobile.setPrice(new BigDecimal(price));
// 				}
// 				String imageUrl = JsoupUtils.firstChildOwnText(autoElement.selectFirst("PhotoUrl"));
// 				if(UrlValidator.getInstance().isValid(imageUrl)) {
// 					automobile.setMainImageUrl(imageUrl);
// 				}
// 				String mpgCity = JsoupUtils.firstChildOwnText(autoElement.selectFirst("MpgCity"));
// 				if(NumberUtils.isDigits(mpgCity)) {
// 					automobile.setMpgCity(Integer.parseInt(mpgCity));
// 				}
// 				String mpgHwy = JsoupUtils.firstChildOwnText(autoElement.selectFirst("MpgHighway"));
// 				if(NumberUtils.isDigits(mpgHwy)) {
// 					automobile.setMpgHighway(Integer.parseInt(mpgHwy));
// 				}
// 				automobile.setDriveType(JsoupUtils.firstChildOwnText(autoElement.selectFirst("DriveTrain")));
// 				automobile.setEngine(JsoupUtils.firstChildOwnText(autoElement.selectFirst("EngineSize")));
// 				String extColor = JsoupUtils.firstChildOwnText(autoElement.selectFirst("ExteriorColor"));
// 				automobile.setExteriorColor(extColor);
// 				String intColor = JsoupUtils.firstChildOwnText(autoElement.selectFirst("InteriorColor"));
// 				automobile.setInteriorColor(intColor);
// 				String city = JsoupUtils.firstChildOwnText(autoElement.selectFirst("StoreCityName"));
// 				State state = State.valueOfAbbreviation(JsoupUtils.firstChildOwnText(autoElement.selectFirst("StoreStateAbbreviation")));
// 				AddressOperations.getAddressFromCityState(city, state).ifPresent(a -> a.setAutomobileAddress(automobile));
// 				automobiles.add(automobile);
// 			}
// 			url = url.replaceAll("&StartIndex=\\d*", "&StartIndex=" + (startIndex += perPage));
// 		}
// 	}
//
// 	private int handleDeletion() {
// 		Calendar calendar = Calendar.getInstance();
// 		calendar.add(Calendar.DATE, -2);
// 		int removed = jdbcTemplate.update("delete from automobile where source_url = ? and visited_date < ?", getSourceUrl(), calendar.getTime());
// 		logger.info("[CarMaxProcess] - Removed [{}] automobiles that were not loaded", removed);
// 		return removed;
// 	}
// }
