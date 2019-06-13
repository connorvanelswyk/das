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

import com.findupon.utilities.PermutableAttribute;
import com.google.common.base.Stopwatch;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


// @Component
public class AutotraderPriceRangeLoader {

	// @Autowired
	private com.findupon.commons.utilities.AutomobileAttributeMatcher attributeMatcher;


	private void getUpdatedModels() {
		File file = new File("/Users/ciminelli/IdeaProjects/chloe/model-price-updates.sql");
		String content;
		try {
			content = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
		} catch(IOException e) {
			e.printStackTrace();
			return;
		}
		System.out.println(Arrays.stream(StringUtils.substringsBetween(content, "where mo.model = '", "' and mk.make = '"))
				.distinct()
				.map(s -> "'" + s + "'")
				.collect(Collectors.joining(",", "(", ")")));
	}

	private void generateUpdateSql() {
		StringBuilder sql = new StringBuilder();
		for(Map.Entry<Integer, PermutableAttribute> makeEntry : attributeMatcher.getFullAttributeMap().entrySet()) {
			String make = makeEntry.getValue().getAttribute();
			for(Map.Entry<Integer, PermutableAttribute> modelEntry : makeEntry.getValue().getChildren().entrySet()) {
				String model = modelEntry.getValue().getAttribute();
				String url = "https://www.autotrader.com/cars-for-sale/" +
						make + "/" +
						model +
						"/Beverly+Hills+CA-90210?zip=90210&startYear=1981&numRecords=25" +
						"&sortBy=derivedpriceASC" +
						"&firstRecord=0&endYear=2019&searchRadius=0";
				url = com.findupon.commons.searchparty.ScoutServices.encodeSpacing(url);
				Pair<Integer, String> minPrices = getFirstPrices(url, true);

				Pair<Integer, String> maxPrices = null;
				if(minPrices != null) {
					url = com.findupon.commons.searchparty.ScoutServices.encodeSpacing(url.replace("&sortBy=derivedpriceASC", "&sortBy=derivedpriceDESC"));
					maxPrices = getFirstPrices(url, false);
				}
				if(minPrices == null || maxPrices == null) {
					sql.append("\n# no price ranges for make: [").append(make).append("]  model: [").append(model).append("]\n");
				} else {
					String update = "\n\nupdate automobile_model_price_range pr join automobile_model mo on pr.model_id = mo.id join automobile_make mk on mo.make_id = mk.id\n" +
							"set   pr.price_min = " + minPrices.getKey() + "\t# " + minPrices.getValue() + "\n" +
							"    , pr.price_max = " + maxPrices.getKey() + "\t# " + maxPrices.getValue() + "\n" +
							"where mo.model = '" + model + "' and mk.make = '" + make + "';\n\n";
					sql.append(update);
				}
			}
		}
		try {
			Files.write(Paths.get("model-price-updates.sql"), sql.toString().getBytes());
		} catch(IOException e) {
			e.printStackTrace();
		}
		System.out.println("\n\n\n\n" + sql.toString());
		try {
			Thread.sleep(1000L);
		} catch(InterruptedException e) {
			e.printStackTrace();
		}
	}

	private Pair<Integer, String> getFirstPrices(String url, boolean min) {
		Stopwatch stopwatch = Stopwatch.createStarted();
		Document document = com.findupon.commons.netops.ConnectionAgent.INSTANCE.stealthDownload(url).getDocument();
		if(document == null) {
			System.err.println("null doc at " + url);
			return null;
		}
		Element header = document.selectFirst("h1[class=\"layout-inline text-xlg\"]");
		if(header == null || StringUtils.containsIgnoreCase(header.html(), "Cars for Sale in")) {
			System.err.println("invalid make model url: " + url);
			return null;
		}
		document.select("div[id=spotlights-container]").remove();
		document.getElementsByAttributeValueContaining("class", "well-primary").remove();
		List<Element> priceElements = document.select("strong[data-qaid=\"cntnr-lstng-price1\"]");
		if(priceElements.isEmpty()) {
			return null;
		}
		String values = priceElements.stream()
				.filter(Element::hasText)
				.map(Element::ownText)
				.limit(3)
				.collect(Collectors.joining(", "));
		String firstPrice = priceElements.get(0).ownText();
		firstPrice = com.findupon.commons.building.PriceOperations.priceStringCleaner(firstPrice);
		Integer price = 0;
		if(com.findupon.commons.utilities.NumUtils.isDigits(firstPrice)) {
			price = Integer.parseInt(firstPrice);
			if(min) {
				if(price <= 5000) {
					price = 1000;
				} else {
					price = Math.round(price / 1000F) * 1000;
					if(price <= 10000) {
						price -= 4000;
					} else if(price <= 20000) {
						price -= 8000;
					} else if(price <= 30000) {
						price -= 10000;
					} else if(price <= 50000) {
						price -= 15000;
					} else if(price <= 75000) {
						price -= 25000;
					} else if(price <= 10000) {
						price -= 30000;
					} else if(price <= 150000) {
						price -= 40000;
					} else if(price <= 200000) {
						price -= 60000;
					} else if(price <= 300000) {
						price -= 70000;
					} else if(price <= 500000) {
						price -= 90000;
					} else if(price <= 1000000) {
						price -= 150000;
					} else if(price > 1000000) {
						price -= 250000;
					}
				}
			} else {
				if(price <= 1000) {
					price = 5000;
				} else {
					price = Math.round(price / 1000F) * 1000;
					if(price <= 10000) {
						price += 4000;
					} else if(price <= 20000) {
						price += 8000;
					} else if(price <= 30000) {
						price += 10000;
					} else if(price <= 50000) {
						price += 15000;
					} else if(price <= 75000) {
						price += 25000;
					} else if(price <= 10000) {
						price += 30000;
					} else if(price <= 150000) {
						price += 40000;
					} else if(price <= 200000) {
						price += 60000;
					} else if(price <= 300000) {
						price += 70000;
					} else if(price <= 500000) {
						price += 90000;
					} else if(price <= 1000000) {
						price += 150000;
					} else if(price > 1000000) {
						price += 250000;
					}
				}
			}
		}
		return Pair.of(price, values);
	}
}
