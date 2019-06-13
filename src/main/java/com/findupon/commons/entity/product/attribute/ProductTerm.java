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

package com.findupon.commons.entity.product.attribute;

import org.apache.commons.lang3.StringUtils;

import javax.persistence.Converter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


public enum ProductTerm implements Attribute.GenericMatching {
	/* NEVER CHANGE THESE IDS */
	OWN(0, Arrays.asList("own", "sale", "for sale", "purchase", "pending", "foreclosure")),
	RENT(1, Arrays.asList("rent", "for rent", "rental")),
	NFS(2, Collections.singletonList("off market")),
	LEASE(3, Collections.singletonList("lease")),
	CHARTER(4, Collections.singletonList("charter"));

	private final Integer id;
	private final List<String> allowedMatches;

	ProductTerm(Integer id, List<String> allowedMatches) {
		this.id = id;
		this.allowedMatches = allowedMatches;
	}

	public static ProductTerm of(Integer id) {
		return Attribute.GenericMatching.of(ProductTerm.class, id);
	}

	public static ProductTerm of(String productStatus) {
		productStatus = StringUtils.replace(productStatus, "_", " ");
		return Attribute.GenericMatching.ofContaining(ProductTerm.class, productStatus);
	}

	@Override
	public Integer getId() {
		return id;
	}

	@Override
	public List<String> getAllowedMatches() {
		return allowedMatches;
	}

	@Converter(autoApply = true)
	public static class ConverterImpl extends Attribute.AbstractConverter<ProductTerm> {
		@Override
		protected Class<ProductTerm> type() {
			return ProductTerm.class;
		}
	}
}
