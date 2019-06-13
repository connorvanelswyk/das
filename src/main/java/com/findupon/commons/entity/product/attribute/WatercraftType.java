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


public enum WatercraftType implements Attribute.GenericMatching {
	/* NEVER CHANGE THESE IDS */
	POWER(0, Collections.singletonList("power")),
	SAIL(1, Arrays.asList("sails", "sail")),
	PWC(2, Arrays.asList("unpowered", "pwc"));


	private final Integer id;
	private final List<String> allowedMatches;

	WatercraftType(Integer id, List<String> allowedMatches) {
		this.id = id;
		this.allowedMatches = allowedMatches;
	}

	public static WatercraftType of(Integer id) {
		return Attribute.GenericMatching.of(WatercraftType.class, id);
	}

	public static WatercraftType of(String watercraftType) {
		return Attribute.GenericMatching.ofContaining(WatercraftType.class, watercraftType);
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
	public static class ConverterImpl extends Attribute.AbstractConverter<WatercraftType> {
		@Override
		protected Class<WatercraftType> type() {
			return WatercraftType.class;
		}
	}
}
