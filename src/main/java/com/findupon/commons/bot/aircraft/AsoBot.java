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

package com.findupon.commons.bot.aircraft;

import com.findupon.commons.building.*;
import com.findupon.commons.entity.building.Address;
import com.findupon.commons.entity.product.BuiltProduct;
import com.findupon.commons.entity.product.aircraft.Aircraft;
import com.findupon.commons.entity.product.attribute.ProductTerm;
import com.findupon.commons.netops.ConnectionAgent;
import com.findupon.commons.searchparty.ScoutServices;
import com.findupon.commons.utilities.JsoupUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.UrlValidator;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Collector;
import org.jsoup.select.Elements;
import org.jsoup.select.Evaluator;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;


public class AsoBot extends AbstractAircraftBot {


	private final List<String> idList = Arrays.asList(
			"Business_Jets",
			"Single_Engine_Pistons",
			"Multi_Engine_Pistons",
			"Business_TurboProps",
			"Turbine_Helicopters",
			"Piston_Helicopters"
	);

	private final String catPagePath = "Aircraft_For_Sale.aspx?act_id=";

	@Override
	public Set<String> retrieveBaseUrls() {
		// we'll need to visit every category page

		idList.forEach(catId -> {
			String url = getDataSource().getUrl() + catPagePath + catId;
			Document document = download(url);
			if(document == null) {
				logger.debug(logPre() + "Base URL came back null [{}]", url);
				return;
			}
			// and collect serp urls from each page
			for(Element makeModelRow : document.select("div.makeModelRow")) {

				Elements makeOrModelRows = makeModelRow.select("div.mmgResultsItemDesc");
				if(makeOrModelRows.size() > 1) {
					// only collect make serp urls when model serp urls do not exist for that make
					makeOrModelRows.remove(0);
				}

				// what remains in makeOrModelRows is either a collection of make serps or a single make serp
				for(Element makeOrModelRow : makeOrModelRows) {
					Element a = makeOrModelRow.selectFirst("a");
					if(a != null && a.hasAttr("href")) {
						baseUrls.add(a.absUrl("href"));
					}
				}
			}
		});
		return baseUrls;
	}

	@Override
	public void gatherProductUrls(List<String> baseUrls, long nodeId) {
		this.nodeId = nodeId;
		for(String baseUrl : baseUrls) {
			Document document = download(baseUrl);
			if(document == null) {
				logger.debug(logPre() + "Base URL came back null [{}]", baseUrl);
				continue;
			}
			document.select("a.photoListingsDescription").stream()
					.filter(e -> e.hasAttr("href"))
					.map(e -> e.absUrl("href"))
					.filter(UrlValidator.getInstance()::isValid)
					.distinct()
					.forEach(productUrls::add);
		}
		listingDataSourceUrlService.bulkInsert(getDataSource(), productUrls, false);
	}


	@Override
	public BuiltProduct buildProduct(String url) {
		String listingId = ScoutServices.getQueryParamValue(url, "id");

		Document document = download(url);
		if(document == null) {
			logger.debug(logPre() + "Document came back null from connection agent [{}]", url);
			return BuiltProduct.removed(listingId);
		}
		Aircraft aircraft = new Aircraft(url);
		aircraft.setProductTerm(ProductTerm.OWN);
		aircraft.setListingId(listingId);

		setYearCategoryMakeModelFromTitle(aircraft, document.title().trim());

		String regNumber = AttributeOperations.getGenericAttributeValue(document,
				AttributeOperations::isAlphaNumericSpaceDash,
				s -> s.length() > 3 && s.length() < 32,
				new HashSet<>(Collections.singletonList("Reg #")),
				new HashSet<>(),
				true);

		String numRegex = ".*[0-9].*";
		String alphaRegex = ".*[A-Z].*";

		if(regNumber != null && regNumber.matches(numRegex) && regNumber.matches(alphaRegex)) {
			aircraft.setRegNumber(StringUtils.upperCase(regNumber));
		}

		String srlNumber = AttributeOperations.getGenericAttributeValue(document,
				AttributeOperations::isAlphaNumericSpaceDash,
				s -> s.length() > 3 && s.length() < 32,
				new HashSet<>(Collections.singletonList("Serial #")),
				new HashSet<>(),
				true);

		JsoupUtils.selectFirst(document, "div[class=adSpecView-header]").ifPresent(e ->
				aircraft.setPrice(AutoParsingOperations.parsePrice(PriceOperations.getPrice(e, AbstractAircraftBot::priceScrubber))));

		Integer hours = AttributeOperations.reduceToInteger(Collector.collect(new Evaluator.ContainsOwnText("TTAF:"), document).text());
		if(hours != null && hours >= 0 && hours <= 100_000) {
			aircraft.setTotalTime(hours);
		}

		String location = Collector.collect(new Evaluator.ContainsOwnText("Location:"), document).text();
		if(!location.equals("")) {
			location = location.toUpperCase();
			location = StringUtils.trimToEmpty(StringUtils.remove(location, "LOCATION:"));
			if(location.contains(",")) {
				String region = StringUtils.trimToEmpty(StringUtils.substringBefore(location, ","));
				String country = StringUtils.trimToEmpty(StringUtils.substringAfter(location, ","));
				if(country.length() <= 3) {
					aircraft.setCountryCode(country);
				}
				if("US".equals(country)) {
					// this won't be accurate because we don't have an address, but better than nothing
					PostalLookupService.getAllPlaces().stream()
							.filter(p -> StringUtils.equalsIgnoreCase(region, p.getAdminCode1()))
							.findFirst()
							.ifPresent(p -> {
								Address a = AddressOperations.mapPlaceToAddress(p, region + ", " + country);
								if(a != null) {
									a.setAircraftAddress(aircraft);
								}
							});
				}
			}
		}

		aircraft.setMainImageUrl(ImageOperations.getMainImageUrl(document, listingId, null, true));
		aircraft.setSrlNumber(StringUtils.upperCase(srlNumber));

		return BuiltProduct.success(aircraft);
	}

	public void buildCountryCodeTable() {
		Set<String> countryCode = new HashSet<>();
		String url = "https://en.wikipedia.org/wiki/List_of_aircraft_registration_prefixes";
		Document document = ConnectionAgent.INSTANCE.stealthDownload(url).getDocument();
		List<String> countries = document.select("table[class=wikitable sortable] td a").eachText();
		document.select("table[class=wikitable sortable] td")
				.forEach(element -> {
					String text = element.ownText().trim();
					if(text.length() > 0 && text.length() < 14
							&& !countries.contains(text)
							&& !element.tagName().equals("a")
							&& !StringUtils.containsIgnoreCase(text, "aa")
							&& !StringUtils.containsIgnoreCase(text, "xx")
							&& !StringUtils.containsIgnoreCase(text, "nnn")
							&& !StringUtils.containsIgnoreCase(text, "see")
							&& !StringUtils.containsIgnoreCase(text, " to ")
							&& !StringUtils.containsIgnoreCase(text, "none")
							&& !StringUtils.containsIgnoreCase(text, "russia")
							&& !StringUtils.containsIgnoreCase(text, "denmark")) {

						// we have a registration prefix or csv of registration prefixes
						if(StringUtils.containsIgnoreCase(text, ",")) {
							for(String string : StringUtils.split(text, ",")) {
								countryCode.add(string.trim());
							}
						} else if(StringUtils.containsIgnoreCase(text, " (")) {
							countryCode.add(StringUtils.substringBefore(text, " ("));
						} else if(StringUtils.containsIgnoreCase(text, "[")) {
							countryCode.add(StringUtils.substringBefore(text, "["));
						} else {
							countryCode.add(text);
						}
					}
				});
		if(countryCode.isEmpty()) {
			System.out.println("fack");
		} else {
			jdbcTemplate.update("insert into aircraft_registration_prefixes(registration_prefix) values "
					+ countryCode.stream()
					.map(s -> "('" + s + "')")
					.collect(Collectors.joining(",")));
		}
	}

	public void buildMakeModelTables() {

		List<String> makeList = jdbcTemplate.queryForList("select make from aircraft_make", String.class);
		List<String> modelList = jdbcTemplate.queryForList("select model from aircraft_model", String.class);

		IntStream.range(0, idList.size()).forEach(catId -> {

			String url = "https://www.aso.com/Aircraft_For_Sale.aspx?act_id=" + idList.get(catId);
			Document document = ConnectionAgent.INSTANCE.stealthDownload(url).getDocument();

			String make = null;
			List<String> models = new ArrayList<>();
			for(Element makeModelRow : document.select("div.makeModelRow")) {

				for(Element makeOrModelRow : makeModelRow.select("div.mmgResultsItemDesc")) {

					Element a = makeOrModelRow.selectFirst("a");
					if(a != null) {

						Element span = a.selectFirst("span");
						if(span != null && span.hasAttr("style")) {

							String style = span.attr("style");
							if(StringUtils.containsIgnoreCase("margin-left:10px;", style)) {
								make = span.text();
								if(make != null) {
									if(make.equals("Cessna / Columbia / Lancair")) {
										make = "Columbia";
									}
									if(make.equals("ERCO / Forney / Alon")) {
										make = "ERCO";
									}
									if(StringUtils.containsIgnoreCase("M20 (", make)) {
										make = "M20";
									}
									if(!makeList.contains(make)) {
										int r = jdbcTemplate.update("insert into aircraft_make(make) values ('" + make + "')");
										if(r > 0) {
											makeList.add(make);
										}
									}
								}
							} else if(StringUtils.containsIgnoreCase("margin-left:25px;", style)) {

								List<String> modelValues = new ArrayList<>();

								String model = span.text();
								if(model == null) {
									continue;
								} else if(StringUtils.containsIgnoreCase(model, "/")) {

									String[] modelSplits = StringUtils.split(model, "/");
									if(modelSplits != null) {

										// if model includes challenger, citation or kingair, prepend
										Optional<String> potentialModelDesignationPrefix =
												Stream.of("Challenger", "King Air", "Citation")
														.filter(s -> StringUtils.containsIgnoreCase(model, s))
														.findAny();

										String modelPrefix = potentialModelDesignationPrefix.orElse(null);

										for(String modelSplit : modelSplits) {
											if(modelSplit != null) {
												String s = StringUtils.remove(modelSplit, modelPrefix);
												s = StringUtils.remove(s, "(");
												s = StringUtils.remove(s, ")");
												s = StringUtils.remove(s, "/");
												s = StringUtils.trim(s);
												if(modelPrefix != null) {
													s = modelPrefix + " " + s;
												}
												modelValues.add(s);
											}
										}
									}
								} else {
									modelValues.add(model);
								}

								for(String modelValue : modelValues) {
									if(modelValue != null && !modelList.contains(modelValue)) {
										int r = jdbcTemplate.update("insert into aircraft_model(model) values ('" + modelValue + "')");
										if(r > 0) {
											modelList.add(modelValue);
											models.add(modelValue);
										}
									}
								}
							}
						}
					}
				}
				insertCatMakeModelRow(catId, make, models);
				models.clear();
			}
		});
	}

	private void insertCatMakeModelRow(Integer catId, String make, List<String> models) {

		String s = "select id from aircraft_make where make = ?";
		Integer makeId = jdbcTemplate.queryForObject(s, Integer.class, make);

		List<Integer> modelIds = new ArrayList<>();
		for(String model : models) {
			String sq = "select id from aircraft_model where model = ?";
			modelIds.add(jdbcTemplate.queryForObject(sq, Integer.class, model));
		}

		if(modelIds.isEmpty()) {
			String ss
					= "select count(*) "
					+ "from aircraft_category_make_model "
					+ "where category_id = ? and make_id = ?";

			Integer exists = jdbcTemplate.queryForObject(ss, Integer.class, catId, makeId);

			if(exists != null && exists < 1) {
				String sql
						= "insert into aircraft_category_make_model "
						+ "(category_id, make_id) values "
						+ "('" + catId + "','" + makeId + "')";

				int re = jdbcTemplate.update(sql);
				if(re > 0) {
//					logger.info("Successfully created a new Aircraft Category Make Model row for cat [{}] and make [{}]", catId, make);
				}
			}
		} else {
			for(Integer modelId : modelIds) {

				String ss
						= "select count(*) "
						+ "from aircraft_category_make_model "
						+ "where category_id = ? and make_id = ? and model_id = ?";

				Integer exists = jdbcTemplate.queryForObject(ss, Integer.class, catId, makeId, modelId);

				if(exists != null && exists < 1) {
					String sql
							= "insert into aircraft_category_make_model "
							+ "(category_id, make_id, model_id) values "
							+ "('" + catId + "','" + makeId + "','" + modelId + "')";

					int re = jdbcTemplate.update(sql);
					if(re > 0) {
//						logger.info("Successfully created a new Aircraft Category Make Model row for cat [{}], make [{}], and modelId [{}]", catId, make, modelId);
					}
				}
			}
		}
	}
}