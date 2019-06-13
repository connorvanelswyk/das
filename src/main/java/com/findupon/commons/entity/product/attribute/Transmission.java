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


public enum Transmission implements Attribute.GenericMatching {
	/* NEVER CHANGE THESE IDS */
	AUTOMATIC(0, Arrays.asList("automatic", "auto", "automated", "auto-shift", "semi-automatic", "semi automatic",
			"cvt", "icvt", "ivt", "hydrostatic", "dual-clutch", "dual clutch", "electrohydraulic", "saxomat")),
	MANUAL(1, Arrays.asList("manual", "6mt", "5mt", "4mt", "stickshift", "stick shift"));

	private final Integer id;
	private final List<String> allowedMatches;

	Transmission(Integer id, List<String> allowedMatches) {
		this.id = id;
		this.allowedMatches = allowedMatches;
	}

	public static Transmission of(Integer id) {
		return Attribute.GenericMatching.of(Transmission.class, id);
	}

	public static Transmission of(String transmission) {
		return Attribute.GenericMatching.of(Transmission.class, transmission);
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
	public static class ConverterImpl extends Attribute.AbstractConverter<Transmission> {
		@Override
		protected Class<Transmission> type() {
			return Transmission.class;
		}
	}
}
