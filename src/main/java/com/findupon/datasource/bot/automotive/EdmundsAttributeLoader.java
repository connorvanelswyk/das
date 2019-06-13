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

import com.plainviewrd.commons.entity.product.attribute.Drivetrain;
import com.plainviewrd.commons.entity.product.attribute.Fuel;
import com.plainviewrd.commons.entity.product.attribute.Transmission;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.PermutableAttribute;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;


@Component
public class EdmundsAttributeLoader {

	@Autowired private com.plainviewrd.commons.utilities.AutomobileAttributeMatcher attributeMatcher;

	private String safeWrap(String value, String prefix, String postfix) {
		return prefix + "'" + value.replace("'", "\\\'") + "'" + postfix;
	}

	private String listValuesSql(List<String> values, String prefix, String postfix) {
		StringBuilder sql = new StringBuilder();
		for(int x = 0; x < values.size(); x++) {
			sql.append(safeWrap(values.get(x), prefix, postfix));
			if(x + 1 < values.size()) {
				sql.append(",");
			}
		}
		return sql.toString();
	}

	public void load() {
		String url = "https://www.edmunds.com/inventory/srp.html" +
				"?inventorytype=used%2Cnew%2Ccpo" +
				"&make=" +
				"&model=" +
				"&radius=500" +
				"&zip=30301";

		StringBuilder sql = new StringBuilder();

		int makeCounter = 0;
		Map<Integer, PermutableAttribute> makes = attributeMatcher.getFullAttributeMap();
		for(Map.Entry<Integer, PermutableAttribute> makeEntry : makes.entrySet()) {

			String make = makeEntry.getValue().getAttribute();
			String makeUrl = url.replace("&make=", "&make=" + com.plainviewrd.commons.searchparty.ScoutServices.encodeSpacing(make));

			int modelCounter = 0;
			Map<Integer, PermutableAttribute> models = attributeMatcher.getFullAttributeMap().get(makeEntry.getKey()).getChildren();
			for(Map.Entry<Integer, PermutableAttribute> modelEntry : models.entrySet()) {

				String model = modelEntry.getValue().getAttribute();
				String modelUrl = makeUrl.replace("&model=", "&model=" + com.plainviewrd.commons.searchparty.ScoutServices.encodeSpacing(model));
				Document modelDoc = com.plainviewrd.commons.netops.ConnectionAgent.INSTANCE.stealthDownload(modelUrl).getDocument();

				Set<Transmission> transmissions = new HashSet<>();
				for(Element transDiv : modelDoc.select("div[data-type=transmission]")) {
					transDiv.select("span[class=\"ui-check-list-item-title mx-1 small\"]").stream()
							.filter(Element::hasText)
							.map(Element::ownText)
							.map(StringUtils::trim)
							.filter(StringUtils::isNotBlank)
							.map(com.plainviewrd.commons.entity.product.attribute.Transmission::of)
							.filter(Objects::nonNull)
							.forEach(transmissions::add);
				}
				Set<Fuel> fuels = new HashSet<>();
				for(Element fuelDiv : modelDoc.select("div[data-type=fuelType]")) {
					fuelDiv.select("span[class=\"ui-check-list-item-title mx-1 small\"]").stream()
							.filter(Element::hasText)
							.map(Element::ownText)
							.map(StringUtils::trim)
							.filter(StringUtils::isNotBlank)
							.map(com.plainviewrd.commons.entity.product.attribute.Fuel::of)
							.filter(Objects::nonNull)
							.forEach(fuels::add);
				}
				Set<Drivetrain> drivetrains = new HashSet<>();
				for(Element intColorDiv : modelDoc.select("div[data-type=driveTrain]")) {
					intColorDiv.select("span[class=\"ui-check-list-item-title mx-1 small\"]").stream()
							.filter(Element::hasText)
							.map(Element::ownText)
							.map(StringUtils::trim)
							.filter(StringUtils::isNotBlank)
							.map(com.plainviewrd.commons.entity.product.attribute.Drivetrain::of)
							.filter(Objects::nonNull)
							.forEach(drivetrains::add);
				}
				for(com.plainviewrd.commons.entity.product.attribute.Transmission transmission : transmissions) {
					sql.append("insert into automobile_model_transmission_xref(model_id, transmission_id) select ")
							.append(modelEntry.getKey()).append(",").append(transmission.getId()).append(";\n");
				}
				for(com.plainviewrd.commons.entity.product.attribute.Fuel fuel : fuels) {
					sql.append("insert into automobile_model_fuel_xref(model_id, fuel_id) select ")
							.append(modelEntry.getKey()).append(",").append(fuel.getId()).append(";\n");
				}
				for(com.plainviewrd.commons.entity.product.attribute.Drivetrain drivetrain : drivetrains) {
					sql.append("insert into automobile_model_drivetrain_xref(model_id, drivetrain_id) select ")
							.append(modelEntry.getKey()).append(",").append(drivetrain.getId()).append(";\n");
				}
				sql.append("\n");
				System.out.println("completed model: " + model + " (" + ++modelCounter + "/" + models.size() + ")\n\n");
			}
			System.out.println("****************************************************\n\n " +
					"completed make: " + make + " (" + ++makeCounter + "/" + makes.size() + ")\n\n" +
					"****************************************************");
		}
		try {
			Files.write(Paths.get("make-model-more-insert.sql"), sql.toString().getBytes());
		} catch(IOException e) {
			e.printStackTrace();
		}
		System.out.println("done");
	}
}
