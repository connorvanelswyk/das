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

import javax.persistence.AttributeConverter;
import java.util.List;
import java.util.Objects;


public class Attribute {

	public interface GenericMatching {

		Integer getId();

		List<String> getAllowedMatches();

		String toString();

		static <T extends GenericMatching> T of(Class<T> clazz, Integer id) {
			if(id == null) {
				return null;
			}
			if(AttributeHelper.incorrectType(clazz)) {
				return null;
			}
			final T[] values = clazz.getEnumConstants();
			if(values == null) {
				return null;
			}
			for(T attribute : values) {
				if(Objects.equals(attribute.getId(), id)) {
					return attribute;
				}
			}
			return null;
		}

		static <T extends GenericMatching> T of(Class<T> clazz, String value) {
			return AttributeHelper.ofValue(clazz, value, false);
		}

		static <T extends GenericMatching> T ofContaining(Class<T> clazz, String value) {
			return AttributeHelper.ofValue(clazz, value, true);
		}
	}

	abstract static class AbstractConverter<T extends GenericMatching> implements AttributeConverter<T, Integer> {

		@Override
		public Integer convertToDatabaseColumn(T attribute) {
			return attribute == null ? null : attribute.getId();
		}

		@Override
		public T convertToEntityAttribute(Integer dbData) {
			return GenericMatching.of(type(), dbData);
		}

		protected abstract Class<T> type();
	}

	private interface AttributeHelper {

		static <T extends GenericMatching> T ofValue(Class<T> clazz, String value, boolean containing) {
			if((value = StringUtils.lowerCase(StringUtils.trimToNull(value))) == null) {
				return null;
			}
			if(incorrectType(clazz)) {
				return null;
			}
			final T[] values = clazz.getEnumConstants();
			if(values == null) {
				return null;
			}
			for(T attribute : values) {
				for(String match : attribute.getAllowedMatches()) {
					if(containing) {
						if(StringUtils.containsIgnoreCase(value, match)) {
							return attribute;
						}
					} else {
						if(StringUtils.equalsIgnoreCase(value, match)) {
							return attribute;
						}
					}
				}
			}
			return null;
		}

		static boolean incorrectType(Class clazz) {
			return clazz == null || !clazz.isEnum();
		}
	}
}
