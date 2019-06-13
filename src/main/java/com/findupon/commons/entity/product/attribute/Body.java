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

import com.google.common.base.CaseFormat;

import javax.persistence.Converter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


public enum Body implements Attribute.GenericMatching {
	/* NEVER CHANGE THESE IDS */
	SEDAN(0, Arrays.asList("sedan", "4 door", "four door")),
	COUPE(1, Arrays.asList("coupe", "coupé", "2 door", "two door")),
	CONVERTIBLE(2, Arrays.asList("convertible", "spyder", "drop top", "drop head")),
	SUV_CROSSOVER(3, Arrays.asList("suv", "crossover", "suv_crossover")),
	VAN_MINIVAN(4, Arrays.asList("van", "minivan", "van_minivan")),
	HATCHBACK(5, Collections.singletonList("hatchback")),
	WAGON(6, Collections.singletonList("wagon")),
	TRUCK(7, Arrays.asList("truck", "pickup"));

	private final Integer id;
	private final List<String> allowedMatches;

	Body(Integer id, List<String> allowedMatches) {
		this.id = id;
		this.allowedMatches = allowedMatches;
	}

	public static Body of(String body) {
		return Attribute.GenericMatching.of(Body.class, body);
	}

	public static Body of(Integer id) {
		return Attribute.GenericMatching.of(Body.class, id);
	}

	@Override
	public Integer getId() {
		return id;
	}

	@Override
	public List<String> getAllowedMatches() {
		return allowedMatches;
	}

	@Override
	public String toString() {
		return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, name());
	}

	@Converter(autoApply = true)
	public static class ConverterImpl extends Attribute.AbstractConverter<Body> {
		@Override
		protected Class<Body> type() {
			return Body.class;
		}
	}
}
