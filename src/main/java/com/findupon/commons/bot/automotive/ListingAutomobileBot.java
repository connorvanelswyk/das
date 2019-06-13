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

import com.google.common.base.Stopwatch;
import com.findupon.commons.bot.ListingBot;
import com.findupon.commons.building.AutoParsingOperations;
import com.findupon.commons.dao.ProductDao;
import com.findupon.commons.entity.product.BuiltProduct;
import com.findupon.commons.entity.product.automotive.Automobile;
import com.findupon.commons.searchparty.AutomotiveGatherer;
import com.findupon.commons.searchparty.ScoutServices;
import com.findupon.commons.utilities.AutomobileAttributeMatcher;
import com.findupon.commons.utilities.TimeUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;


@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public abstract class ListingAutomobileBot extends ListingBot<Automobile> {

	@Autowired protected AutomobileAttributeMatcher attributeMatcher;
	@Autowired protected AutomotiveGatherer automotiveGatherer;


	BuiltProduct standardBuilder(String url) {
		return standardBuilder(url, (document, automobile) -> {});
	}

	BuiltProduct standardBuilder(String url, BiConsumer<Document, Automobile> additionalAttributeSetter) {
		String listingId = ScoutServices.getUniqueIdentifierFromUrl(url, null, AutoParsingOperations.vinRecognizer());
		Document document = download(url);
		if(document == null) {
			logger.debug(logPre() + "Document came back null from connection agent [{}]", url);
			return BuiltProduct.removed(listingId);
		}
		Automobile automobile = automotiveGatherer.buildProduct(document);
		if(automobile == null) {
			logger.debug(logPre() + "Automobile came back null from generic builder [{}]", url);
			return BuiltProduct.removed(listingId);
		} else {
			additionalAttributeSetter.accept(document, automobile);
			return BuiltProduct.success(automobile);
		}
	}

	/**
	 * @implNote MAKE SURE YOUR {@param serpAutomobileBuilder}
	 * CALLS {@link ListingAutomobileBot#validateSetMetaAndAdd} TO ADD AUTOMOBILES
	 * ... and returns the number of products built
	 */
	void indexOnlyGathering(long nodeId, List<String> baseUrls, Function<String, Integer> serpAutomobileBuilder) {
		this.nodeId = nodeId;
		int totalBuilt = 0, totalUrls = baseUrls.size(), completedUrls = 0;
		Stopwatch stopwatch = Stopwatch.createStarted();
		int reportSize = ObjectUtils.defaultIfNull(getDataSource().getIndexDelegationSize(), 20);
		if(reportSize >= 3) {
			reportSize /= 3;
		}
		if(reportSize <= 0) {
			reportSize = 1;
		}
		for(String url : baseUrls) {
			totalBuilt += serpAutomobileBuilder.apply(url);
			completedUrls++;
			if(sleep()) {
				return;
			}
			if(products.size() >= ProductDao.productWriteThreshold) {
				logger.info(logPre() + "Persisting [{}] products to the db (over threshold from base)", products.size());
				persistAndClear();
			}
			if(completedUrls % reportSize == 0) {
				logger.info(logPre() + String.format("Total cars: [%d] Cars per second: [%.2f] URLs completed [%d/%d] Total time: [%s] ",
						totalBuilt, (float)totalBuilt / stopwatch.elapsed(TimeUnit.SECONDS), completedUrls, totalUrls, TimeUtils.format(stopwatch)));
			}
		}
		if(!products.isEmpty()) {
			logger.info(logPre() + "Persisting [{}] products to the db (final)", products.size());
			persistAndClear();
		}
		logger.info(logPre() + "Serp building complete. Autos built: [{}] Base URLs: [{}] Time taken: [{}]", totalBuilt, totalUrls, TimeUtils.format(stopwatch));
	}
}
