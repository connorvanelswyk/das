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
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import javax.validation.constraints.NotNull;
import java.util.Arrays;
import java.util.concurrent.atomic.LongAdder;


@Component
public class BasicKeywordFrequency implements AssetDeterminer {

	private static final String[] keywords = {
			"inventory", "service", "parts", "pre-owned", "cars", "vehicle", "auto", "dealer"
	};
	private final double frequencyThreshold = 3D / keywords.length; // where 3 is the num of matches needed


	@Override
	public PossibleAssetType getPossibleAssetType(@NotNull Document document) {
		PossibleAssetType possibleAssetType = new PossibleAssetType();
		possibleAssetType.setAssetType(AssetType.AUTOMOBILE);

		LongAdder found = new LongAdder();
		Arrays.stream(keywords)
				.filter(keyword -> StringUtils.containsIgnoreCase(document.html(), keyword))
				.forEach(keyword -> found.increment());

		double frequency = found.doubleValue() / keywords.length;
		possibleAssetType.setGuess(frequency >= frequencyThreshold);
		possibleAssetType.setRelativeProbability(frequency);
		return possibleAssetType;
	}
}
