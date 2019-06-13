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


public enum RealEstateType implements Attribute.GenericMatching {
	/* NEVER CHANGE THESE IDS */
	HOUSE(0, Arrays.asList("home", "house", "single family home", "single-family home", "family home", "single family", "single-family")),
	APARTMENT_CONDO(1, Arrays.asList("apartment", "condo", "condominium", "loft")),
	TOWN_HOME(2, Arrays.asList("town home", "town-home", "townhome", "townhouse", "town house", "town-house")),
	MULTI_FAMILY(3, Arrays.asList("multi-family", "multi-family home")),
	LAND(4, Arrays.asList("land", "lot/land", "land", "property")),
	MOBILE(5, Arrays.asList("mobile", "mobile home")),
	OTHER(6, Collections.singletonList("other"));

	private final Integer id;
	private final List<String> allowedMatches;

	RealEstateType(Integer id, List<String> allowedMatches) {
		this.id = id;
		this.allowedMatches = allowedMatches;
	}

	public static RealEstateType of(Integer id) {
		return Attribute.GenericMatching.of(RealEstateType.class, id);
	}

	public static RealEstateType of(String realEstateType) {
		realEstateType = StringUtils.replace(realEstateType, "_", " ");
		return Attribute.GenericMatching.of(RealEstateType.class, realEstateType);
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
	public static class ConverterImpl extends Attribute.AbstractConverter<RealEstateType> {
		@Override
		protected Class<RealEstateType> type() {
			return RealEstateType.class;
		}
	}
}
