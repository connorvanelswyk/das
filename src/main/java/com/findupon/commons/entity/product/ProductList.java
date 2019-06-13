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

package com.findupon.commons.entity.product;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;


public class ProductList<P extends Product & Serializable> extends ArrayList<P> implements Serializable {
	private static final long serialVersionUID = -8561357227698834478L;
	private static final Logger logger = LoggerFactory.getLogger(ProductList.class);

	/**
	 * This does nothing more than convert the multiple database results to a single product, logging an error to sentry if
	 * more than one result was returned, as such a case would mean products are not getting inserted properly or our selecting
	 * conditions are too broad. Either way, updates would have to be made.
	 *
	 * Did I need to make our own list just for this convenience function as a static utility would be functional equivalent?
	 * No, but its cool.
	 */
	public Optional<P> warningReducer() {
		if(!this.isEmpty()) {
			P product = this.get(0);
			if(this.size() > 1) {
				logger.error("[ProductDataService] - More than one product found during existing lookup! Type: [{}] IDs: [{}]",
						product.getClass().getSimpleName(),
						this.stream().map(P::getId).map(String::valueOf).collect(Collectors.joining(", ")));
			}
			return Optional.of(product);
		}
		return Optional.empty();
	}
}
