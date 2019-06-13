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

package com.findupon.commons.learning;

import com.findupon.commons.building.AttributeOperations;
import com.findupon.commons.entity.building.TagWeight;
import com.findupon.commons.entity.datasource.AssetType;
import com.findupon.commons.entity.learning.PossibleAssetType;
import com.findupon.commons.utilities.AutomobileAttributeMatcher;
import com.findupon.commons.utilities.JsoupUtils;
import com.findupon.utilities.ContainsCollectionOwnText;
import com.findupon.utilities.PermutableAttribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Collector;
import org.jsoup.select.Evaluator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


@Component
public class KeyAttributeFrequency implements AssetDeterminer {
	@Autowired private AutomobileAttributeMatcher attributeMatcher;

	@Override
	public PossibleAssetType getPossibleAssetType(@NotNull Document document) {
		List<Double> attributeWeights = new ArrayList<>();
		Set<String> makes = attributeMatcher.getFullAttributeMap().values().stream()
				.map(PermutableAttribute::getAttribute)
				.collect(Collectors.toSet());

		List<Element> elements = Collector.collect(new ContainsCollectionOwnText(makes), document).stream()
				.filter(JsoupUtils.defaultTextQualityGate)
				.collect(Collectors.toList());

		for(String attribute : makes) {
			for(Element e : elements) {
				String text = e.ownText();
				if(AttributeOperations.containsLoneAttribute(text, attribute)) {
					attributeWeights.add(TagWeight.getWeight(e));
				}
			}
		}
		double weight = attributeWeights.stream().reduce(0D, Double::sum);

		PossibleAssetType possibleAssetType = new PossibleAssetType();
		possibleAssetType.setGuess(weight >= TagWeight.DEFAULT_WEIGHT * 4);
		possibleAssetType.setRelativeProbability(weight > 1 ? 1 : weight);
		possibleAssetType.setAssetType(AssetType.AUTOMOBILE);
		return possibleAssetType;
	}
}
