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
import com.findupon.commons.searchparty.ScoutServices;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

import static com.findupon.commons.utilities.ConsoleColors.*;


@Component
public class AssetRecognizer {
	private static final Logger logger = LoggerFactory.getLogger(AssetRecognizer.class);

	@Autowired private KeyAttributeFrequency keyAttributeFrequency;
	@Autowired private BasicKeywordFrequency basicKeywordFrequency;
	@Autowired private URLKeywordFrequency urlKeywordFrequency;
	@Autowired private MetaFrequency metaFrequency;
	@Autowired private AddressFinder addressFinder;
	@Autowired private SchemaFinder schemaFinder;

	private static final double correctGuessThreshold = 3D / 6D;


	public Optional<AssetType> determineAssetType(Document document) {
		if(document == null) {
			return Optional.empty();
		}
		List<PossibleAssetType> possibleAssetTypes = new ArrayList<>();
		possibleAssetTypes.add(keyAttributeFrequency.getPossibleAssetType(document));
		possibleAssetTypes.add(basicKeywordFrequency.getPossibleAssetType(document));
		possibleAssetTypes.add(urlKeywordFrequency.getPossibleAssetType(document));
		possibleAssetTypes.add(metaFrequency.getPossibleAssetType(document));
		possibleAssetTypes.add(addressFinder.getPossibleAssetType(document));
		possibleAssetTypes.add(schemaFinder.getPossibleAssetType(document));

		Map<AssetType, List<PossibleAssetType>> correctAssetTypeGuesses = new LinkedHashMap<>();
		for(PossibleAssetType possibleAssetType : possibleAssetTypes) {
			if(possibleAssetType.isGuess() && possibleAssetType.getAssetType() != null) {
				correctAssetTypeGuesses.computeIfAbsent(possibleAssetType.getAssetType(), x -> new ArrayList<>()).add(possibleAssetType);
			}
		}
		if(correctAssetTypeGuesses.keySet().size() > 1) {
			logger.info("More than one asset type determined, no guess will be made. Types: [{}] URL: [{}]",
					correctAssetTypeGuesses.keySet().stream().map(AssetType::toString).collect(Collectors.joining(", ")), document.location());
			return Optional.empty();
		}
		if(correctAssetTypeGuesses.isEmpty()) {
			logger.info("No asset types determined for URL [{}]", document.location());
			return Optional.empty();
		}
		LongAdder numCorrectGuesses = new LongAdder();
		DoubleAdder totalRelativeProbability = new DoubleAdder();
		AssetType assetType = correctAssetTypeGuesses.keySet().iterator().next();
		correctAssetTypeGuesses.get(assetType)
				.forEach(p -> {
					numCorrectGuesses.increment();
					totalRelativeProbability.add(p.getRelativeProbability());
				});
		double confidence = numCorrectGuesses.doubleValue() / possibleAssetTypes.size();
		boolean guess = confidence >= correctGuessThreshold;

		String domain = StringUtils.defaultString(ScoutServices.getDomain(document.location()));
		if(domain.length() > 32) {
			domain = domain.substring(0, 32);
		}
		String log = String.format("%-40s %-33s %-13s %-28s %-28s %s",
				"URL: [" + domain + "]",
				"Asset: [" + cyan(assetType.toString()) + "]",
				"Guess: [" + (guess ? "✅" : "❌") + "]",
				"C/T: [" + String.format(guess ? green("%.2f%%") : red("%.2f%%"), confidence * 100) + "]",
				"Thrs: [" + green(String.format("%.2f%%", correctGuessThreshold * 100)) + "]",
				"Avg Prob: [" + String.format("%.2f%%", totalRelativeProbability.doubleValue() / numCorrectGuesses.longValue() * 100) + "]"
		);
		logger.info(log);

		if(guess) {
			return Optional.of(assetType);
		} else {
			return Optional.empty();
		}
	}
}
