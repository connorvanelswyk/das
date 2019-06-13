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


public enum InteriorColor implements Attribute.GenericMatching {
	/* NEVER CHANGE THESE IDS */
	WHITE(1, Arrays.asList("white", "ivory", "wht")),
	GRAY(2, Arrays.asList("gray", "grey", "charcoal", "slate", "graystone")),
	BLACK(3, Arrays.asList("black", "emerald", "ebony", "blk")),
	BROWN(4, Arrays.asList("brown", "chocolate", "burgundy", "umber", "russet", "mocha")),
	BEIGE(5, Arrays.asList("beige", "sand", "cream", "latte")),
	TAN(6, Arrays.asList("tan", "khaki", "taupe")),
	RED(7, Arrays.asList("red", "rose", "ruby", "crimson"));

	private final Integer id;
	private final List<String> allowedMatches;

	InteriorColor(Integer id, List<String> allowedMatches) {
		this.id = id;
		this.allowedMatches = allowedMatches;
	}

	public static InteriorColor of(String color) {
		return Attribute.GenericMatching.ofContaining(InteriorColor.class, color);
	}

	public static InteriorColor of(Integer id) {
		return Attribute.GenericMatching.of(InteriorColor.class, id);
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
	public static class ConverterImpl extends Attribute.AbstractConverter<InteriorColor> {
		@Override
		protected Class<InteriorColor> type() {
			return InteriorColor.class;
		}
	}
}
