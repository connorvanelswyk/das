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
import java.util.List;


public enum Fuel implements Attribute.GenericMatching {
	/* NEVER CHANGE THESE IDS */
	GASOLINE(0, Arrays.asList("gasoline", "gas", "regular unleaded", "premium unleaded")),
	DIESEL(1, Arrays.asList("diesel", "tdi")),
	HYBRID(2, Arrays.asList("hybrid", "activehybrid", "engine and electric", "engine & electric", "energi")),
	ELECTRIC(3, Arrays.asList("electric", "ev"));

	private final Integer id;
	private final List<String> allowedMatches;

	Fuel(Integer id, List<String> allowedMatches) {
		this.id = id;
		this.allowedMatches = allowedMatches;
	}

	public static Fuel of(Integer id) {
		return Attribute.GenericMatching.of(Fuel.class, id);
	}

	public static Fuel of(String fuel) {
		return Attribute.GenericMatching.of(Fuel.class, fuel);
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
	public static class ConverterImpl extends Attribute.AbstractConverter<Fuel> {
		@Override
		protected Class<Fuel> type() {
			return Fuel.class;
		}
	}
}
