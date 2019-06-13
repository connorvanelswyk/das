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

package com.findupon.commons.entity.product;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;


/**
 * todo convert to xref table
 */
public enum ProductCondition {

	NEW(0),
	USED(1);

	private final Integer id;

	ProductCondition(Integer id) {
		this.id = id;
	}

	public Integer getId() {
		return id;
	}

	public static ProductCondition of(Integer id) {
		if(id == null) {
			return null;
		}
		for(ProductCondition productType : values()) {
			if(productType.id.equals(id)) {
				return productType;
			}
		}
		return null;
	}

	@Converter(autoApply = true)
	public static class ConverterImpl implements AttributeConverter<ProductCondition, Integer> {

		@Override
		public Integer convertToDatabaseColumn(ProductCondition attribute) {
			return attribute == null ? null : attribute.id;
		}

		@Override
		public ProductCondition convertToEntityAttribute(Integer dbData) {
			return of(dbData);
		}
	}
}