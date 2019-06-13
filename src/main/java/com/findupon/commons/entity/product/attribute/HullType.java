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

import javax.persistence.Converter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


public enum HullType implements Attribute.GenericMatching {
	/* NEVER CHANGE THESE IDS */
	ALUMINUM(0, Collections.singletonList("aluminum")),
	COMPOSITE(1, Collections.singletonList("composite")),
	FERROCEMENT(2, Collections.singletonList("ferro-cement")),
	FIBERGLASS(3, Collections.singletonList("fiberglass")),
	HYPALON(4, Collections.singletonList("hypalon")),
	INFLATABLE(5, Arrays.asList("inflatable", "pvc")),
	PLASTIC(6, Collections.singletonList("plastic")),
	ROPLENE(7, Collections.singletonList("roplene")),
	STEEL(8, Collections.singletonList("steel")),
	WOOD(9, Collections.singletonList("wood"));

	private final Integer id;
	private final List<String> allowedMatches;

	HullType(Integer id, List<String> allowedMatches) {
		this.id = id;
		this.allowedMatches = allowedMatches;
	}

	public static HullType of(Integer id) {
		return Attribute.GenericMatching.of(HullType.class, id);
	}

	public static HullType of(String HullType) {
		return Attribute.GenericMatching.ofContaining(HullType.class, HullType);
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
	public static class ConverterImpl extends Attribute.AbstractConverter<HullType> {
		@Override
		protected Class<HullType> type() {
			return HullType.class;
		}
	}
}
