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


public enum Drivetrain implements Attribute.GenericMatching {
	/* NEVER CHANGE THESE IDS */
	FRONT_WHEEL_DRIVE(0, Arrays.asList("front wheel drive", "fwd", "front_wheel_drive")),
	REAR_WHEEL_DRIVE(1, Arrays.asList("rear wheel drive", "rwd", "rear_wheel_drive")),
	ALL_WHEEL_DRIVE(2, Arrays.asList("all wheel drive", "awd", "xdrive", "all_wheel_drive")),
	FOUR_WHEEL_DRIVE(3, Arrays.asList("four wheel drive", "4wd", "4x4", "four_wheel_drive"));

	private final Integer id;
	private final List<String> allowedMatches;

	Drivetrain(Integer id, List<String> allowedMatches) {
		this.id = id;
		this.allowedMatches = allowedMatches;
	}

	public static Drivetrain of(Integer id) {
		return Attribute.GenericMatching.of(Drivetrain.class, id);
	}

	public static Drivetrain of(String drivetrain) {
		return Attribute.GenericMatching.of(Drivetrain.class, drivetrain);
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
	public static class ConverterImpl extends Attribute.AbstractConverter<Drivetrain> {
		@Override
		protected Class<Drivetrain> type() {
			return Drivetrain.class;
		}
	}
}
