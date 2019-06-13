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

import com.findupon.commons.entity.datasource.AssetType;
import com.findupon.commons.entity.learning.PossibleAssetType;
import com.findupon.commons.utilities.AutomobileAttributeMatcher;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.validation.constraints.NotNull;
import java.util.Set;
import java.util.TreeSet;


@Component
public class URLKeywordFrequency implements AssetDeterminer {
	@Autowired private AutomobileAttributeMatcher attributeMatcher;
	private final Set<String> keywords = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

	@Override
	public PossibleAssetType getPossibleAssetType(@NotNull Document document) {
		PossibleAssetType possibleAssetType = new PossibleAssetType();
		possibleAssetType.setAssetType(AssetType.AUTOMOBILE);
		if(keywords.isEmpty()) {
			buildKeywords();
		}
		if(keywords.stream().anyMatch(s -> StringUtils.containsIgnoreCase(document.location(), s))) {
			possibleAssetType.setGuess(true);
			possibleAssetType.setRelativeProbability(1D);
		} else {
			possibleAssetType.setGuess(false);
			possibleAssetType.setRelativeProbability(0D);
		}
		return possibleAssetType;
	}

	private void buildKeywords() {
		attributeMatcher.getFullAttributeMap().entrySet().stream()
				.map(e -> e.getValue().getAttribute()).forEach(s -> {
			keywords.add(s);
			if(s.contains("-")) {
				keywords.add(s.replace("-", ""));
			}
			if(s.contains(" ")) {
				keywords.add(s.replace(" ", "-"));
				keywords.add(s.replace(" ", ""));
			}
		});
		keywords.add("vw");
		keywords.add("auto");
		keywords.add("cars");
		keywords.add("vehicle");
		keywords.add("dealer");
		keywords.add("drive");
	}
}
