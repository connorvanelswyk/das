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

import com.neovisionaries.i18n.CountryCode;
import com.findupon.commons.bot.ListingBot;
import com.findupon.commons.building.PriceOperations;
import com.findupon.commons.entity.product.ProductCondition;
import com.findupon.commons.entity.product.aircraft.Aircraft;
import com.findupon.commons.entity.product.aircraft.AircraftCategory;
import com.findupon.commons.entity.product.aircraft.AircraftMake;
import com.findupon.commons.utilities.AircraftAttributeMatcher;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.IntStream;


@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public abstract class AbstractAircraftBot extends ListingBot<Aircraft> {

	@Autowired protected AircraftAttributeMatcher attributeMatcher;
	@Autowired protected JdbcTemplate jdbcTemplate;

	private static final Set<String> makeKeys = new HashSet<>(Arrays.asList("manufacturer"));
	private static final Set<String> modelKeys = new HashSet<>(Arrays.asList("model"));
	private static final Set<String> locationKeys = new HashSet<>(Arrays.asList("location:"));
	private static final Set<String> ttafTagKeys = new HashSet<>(Arrays.asList("Total Time", "ttaf:"));
	private static final Set<String> priceTagKeys = new HashSet<>(Arrays.asList("price:", "price"));
	private static final Set<String> regNumKeys = new HashSet<>(Arrays.asList("registration #", "reg #"));
	private static final Set<String> srlNumKeys = new HashSet<>(Arrays.asList("serial #", "ser #", "srl #"));
	// Number of Seats
	// Year

	void setAttribute(Aircraft aircraft, String text) {
		if(aircraft == null || StringUtils.isBlank(text)) {
			return;
		}
		if(aircraft.getMake() == null) {
			makeKeys.forEach(makeKey -> {
				if(StringUtils.containsIgnoreCase(text, makeKey)) {
					List<String> chunks = Arrays.asList(StringUtils.split(text, makeKey));
					int chunkSize = chunks.size();
					IntStream.range(0, chunkSize).forEach(i -> {
						String make = StringUtils.trimToNull(chunks.get(1));
						Integer makeId = attributeMatcher.getMakeId(make);
						if(makeId == -1 && chunkSize > 2) {
							// could be a two word make
							make += StringUtils.trimToNull(chunks.get(2));
							makeId = attributeMatcher.getMakeId(make);
						}
						if(makeId != -1) {
							aircraft.setMakeId(makeId);
							aircraft.setMake(make);
						}
					});
				}
			});
		}
		if(aircraft.getModel() == null) {
			modelKeys.forEach(modelKey -> {
				if(StringUtils.containsIgnoreCase(text, modelKey)) {
					List<String> chunks = Arrays.asList(StringUtils.split(text, modelKey));
					int chunkSize = chunks.size();
					IntStream.range(0, chunkSize).forEach(i -> {
						String model = StringUtils.trimToNull(chunks.get(1));
						Integer modelId = attributeMatcher.getModelId(model);
						if(modelId == -1 && chunkSize > 2) {
							// could be a two word model
							model += StringUtils.trimToNull(chunks.get(2));
							modelId = attributeMatcher.getModelId(model);
						}
						if(modelId != -1) {
							aircraft.setModelId(modelId);
							aircraft.setModel(model);
						}
					});
				}
			});
		}
		if(aircraft.getCategory() == null && aircraft.getMakeId() != null) {
			IntStream.range(0, attributeMatcher.getFullAttributeMap().size()).forEach(i -> {
				AircraftCategory aircraftCategory = (AircraftCategory)attributeMatcher.getFullAttributeMap().get(1);
				aircraftCategory.getChildren().forEach((key, value) -> {
					if(key.equals(aircraft.getMakeId())) {
						if(aircraft.getModelId() != null) {
							value.getChildren().forEach((k, v) -> {
								if(k.equals(aircraft.getModelId())) {
									aircraft.setCategoryId(i);
								}
							});
						} else {
							aircraft.setCategoryId(i);
						}
					}
				});
			});
		}
		if(aircraft.getYear() == null && StringUtils.containsIgnoreCase(text, "year")) {
			List<String> chunks = Arrays.asList(StringUtils.split(text, "year"));
			String yearChunk = StringUtils.trimToNull(chunks.get(1));
			if(StringUtils.isNumeric(yearChunk)) {
				try {
					aircraft.setYear(Integer.valueOf(yearChunk));
				} catch(Exception e) {
					logger.error("Bad year", e);
				}
			}
		}
		if(aircraft.getRegNumber() == null) {
			regNumKeys.forEach(regNumKey -> {
				if(StringUtils.containsIgnoreCase(text, regNumKey)) {
					List<String> chunks = Arrays.asList(StringUtils.split(text, regNumKey));
					IntStream.range(0, chunks.size()).forEach(i -> {
						if(StringUtils.containsIgnoreCase(regNumKey, chunks.get(i))) {
							String regNumVal = StringUtils.trimToNull(chunks.get(i + 1));
							if(attributeMatcher.containsValidRegistrationPrefix(regNumVal)) {
								aircraft.setRegNumber(regNumVal);
							}
						}
					});
				}
			});
		}
		if(aircraft.getSrlNumber() == null) {
			srlNumKeys.forEach(srlNumKey -> {
				if(StringUtils.containsIgnoreCase(text, srlNumKey)) {
					List<String> chunks = Arrays.asList(StringUtils.split(text, srlNumKey));
					IntStream.range(0, chunks.size()).forEach(i -> {
						if(StringUtils.containsIgnoreCase(srlNumKey, chunks.get(i))) {
							aircraft.setSrlNumber(StringUtils.trimToNull(chunks.get(i + 1)));
						}
					});
				}
			});
		}
		if(aircraft.getPrice() == null) {
			priceTagKeys.forEach(priceTagKey -> {
				if(StringUtils.containsIgnoreCase(text, priceTagKey)) {
					List<String> chunks = Arrays.asList(StringUtils.split(text, priceTagKey));
					IntStream.range(0, chunks.size()).forEach(i -> {
						if(StringUtils.containsIgnoreCase(priceTagKey, chunks.get(i))) {
							String price = PriceOperations.priceStringCleaner(chunks.get(i + 1));
							if(StringUtils.isNumeric(price)) {
								aircraft.setPrice(new BigDecimal(price));
							}
						}
					});
				}
			});
		}
		if(aircraft.getTotalTime() == null) {
			ttafTagKeys.forEach(ttafTagKey -> {
				if(StringUtils.containsIgnoreCase(text, ttafTagKey)) {
					List<String> chunks = Arrays.asList(StringUtils.split(text, ttafTagKey));
					IntStream.range(0, chunks.size()).forEach(i -> {
						if(StringUtils.containsIgnoreCase(ttafTagKey, chunks.get(i))) {
							String[] ttChunks = StringUtils.split(chunks.get(1), " ");
							if(ttChunks != null) {
								String tt = StringUtils.trimToNull(ttChunks[0]);
								if(StringUtils.containsIgnoreCase(tt, ".")) {
									tt = StringUtils.substringBeforeLast(tt, ".");
								}
								if(StringUtils.containsIgnoreCase(tt, ",")) {
									tt = StringUtils.remove(tt, ",");
								}
								if(StringUtils.isNumeric(tt)) {
									aircraft.setTotalTime(Integer.valueOf(tt));
								}
							}
						}
					});
				}
			});
		}
		if(aircraft.getTotalTime() != null && aircraft.getTotalTime() > 0) {
			aircraft.setProductCondition(ProductCondition.USED);
		}
		if(aircraft.getLatitude() == null || aircraft.getLongitude() == null) {
			locationKeys.forEach(locationKey -> {
				if(StringUtils.containsIgnoreCase(text, locationKey)) {
					List<String> chunks = Arrays.asList(StringUtils.split(text, locationKey));
					IntStream.range(0, chunks.size()).forEach(i -> {
						if(StringUtils.containsIgnoreCase(locationKey, chunks.get(i))) {
							List<String> locChunks = Arrays.asList(StringUtils.split(chunks.get(i + 1), ", "));
							if(CollectionUtils.isNotEmpty(locChunks) && chunks.size() > 1) {
								String country = StringUtils.trimToEmpty(locChunks.get(1));
								if(!CountryCode.valueOf(country).equals(CountryCode.UNDEFINED)) {
									aircraft.setCountryCode(country);
									aircraft.setCity(locChunks.get(0));
								}
							}
						}
					});
				}
			});
		}
	}

	/*
				locationKeys.stream()
					.filter(lk -> StringUtils.containsIgnoreCase(text, lk))
					.flatMap(lk -> {
						String[] split = StringUtils.split(text, lk);
						if(split == null) {
							return Stream.empty();
						}
						return Arrays.stream(split);
					})
					.filter(chunk -> )
	 */


	/**
	 * eg: '2004 Socata TBM 700C2 299 N48UM for Sale: Specs, Price | ASO.com'
	 */
	void setYearCategoryMakeModelFromTitle(Aircraft aircraft, String title) {
		final String finalTitle = StringUtils.trimToNull(title);
		if(finalTitle != null) {

			String[] titleChunks = StringUtils.split(finalTitle, " ");

			String yearChunk = titleChunks[0];
			if(yearChunk.matches("^\\d{4}$")) {

				Integer year = Integer.valueOf(yearChunk);
				if(year > 1900 && year < 2020) {
					aircraft.setYear(year);
				}

				String make = titleChunks[1];
				int makeId = attributeMatcher.getMakeId(make);
				if(makeId == -1) {
					// this could be the full make name or part of a make name
					make += titleChunks[2];
					makeId = attributeMatcher.getMakeId(make);
				}

				if(makeId != -1) {
					aircraft.setMakeId(makeId);

					attributeMatcher.getFullAttributeMap().forEach((catKey, catVal) -> {

						AircraftMake aircraftMake = (AircraftMake)catVal.getChildren().get(aircraft.getMakeId());
						if(aircraftMake != null) {

							aircraft.setMake(aircraftMake.getAttribute());
							aircraftMake.getChildren().forEach((k, v) -> {
								if(StringUtils.containsIgnoreCase(finalTitle, v.getAttribute())) {
									aircraft.setCategory(catVal.getAttribute());
									aircraft.setCategoryId(catKey);
									aircraft.setModelId(k);
									aircraft.setModel(v.getAttribute());
								}
							});
						}
					});
				}
			}
		}
	}

	static void priceScrubber(List<String> priceValues) {
		if(priceValues.isEmpty()) {
			return;
		}
		Map<String, Double> doubleValues = new HashMap<>();
		priceValues.stream()
				.filter(NumberUtils::isDigits)
				.forEach(s -> doubleValues.putIfAbsent(s, Double.parseDouble(s)));
		for(Map.Entry<String, Double> entry : doubleValues.entrySet()) {
			if(entry.getValue() < 10_000 || entry.getValue() > 250_000_000) {
				priceValues.removeAll(Collections.singleton(entry.getKey()));
			}
		}
	}
}
