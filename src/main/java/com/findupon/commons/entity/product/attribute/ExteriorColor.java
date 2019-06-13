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


public enum ExteriorColor implements Attribute.GenericMatching {
	/* NEVER CHANGE THESE IDS */
	WHITE(1, Arrays.asList("white", "ivory", "wht")),
	SILVER(2, Arrays.asList("silver", "titanium", "silvermist", "sterling")),
	GRAY(3, Arrays.asList("gray", "grey", "graphite", "pewter", "slate", "gunmetal", "steel metallic")),
	BLACK(4, Arrays.asList("black", "emerald", "ebony", "blk", "charcoal")),
	RED(5, Arrays.asList("red", "burgundy", "scarlet", "crimson", "maroon", "rose", "ruby", "carmine", "amaranth")),
	BROWN(6, Arrays.asList("brown", "chocolate", "burgundy", "tan", "beige", "taupe", "sand")),
	ORANGE(7, Arrays.asList("orange", "apricot", "amber", "coral")),
	GOLD(8, Arrays.asList("gold", "champagne", "bronze")),
	YELLOW(9, Arrays.asList("yellow", "canary", "ocher")),
	GREEN(10, Arrays.asList("green", "jade", "viridian", "mint", "celadon")),
	BLUE(11, Arrays.asList("blue", "periwinkle", "turquoise", "cyan", "aquamarine", "aqua", "teal", "azure", "cerulean")),
	PURPLE(12, Arrays.asList("purple", "indigo", "violet", "mauve", "amethyst", "byzantium")),
	PINK(13, Arrays.asList("pink", "cerise"));

	private final Integer id;
	private final List<String> allowedMatches;

	ExteriorColor(Integer id, List<String> allowedMatches) {
		this.id = id;
		this.allowedMatches = allowedMatches;
	}

	public static ExteriorColor of(String color) {
		return Attribute.GenericMatching.ofContaining(ExteriorColor.class, color);
	}

	public static ExteriorColor of(Integer id) {
		return Attribute.GenericMatching.of(ExteriorColor.class, id);
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
	public static class ConverterImpl extends Attribute.AbstractConverter<ExteriorColor> {
		@Override
		protected Class<ExteriorColor> type() {
			return ExteriorColor.class;
		}
	}
}
