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

package com.findupon.commons.netops.entity;

import org.apache.commons.lang3.StringUtils;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;


public enum ProxyMode {

	PUBLIC(false), // no proxy will be used
	ROTATE_LOCATION(true), // choose the proxy closest to the connecting server. rotate IPs at random intervals in range.
	ROTATE_OPEN(true); // only use the open proxy. rotate IPs at random intervals in range.


	private final boolean tunneled;

	ProxyMode(boolean tunneled) {
		this.tunneled = tunneled;
	}

	public boolean isTunneled() {
		return tunneled;
	}

	private static ProxyMode valueOfMode(String mode) {
		mode = StringUtils.trimToNull(mode);
		if(mode == null) {
			throw new IllegalArgumentException("Proxy mode cannot be null");
		}
		return valueOf(mode.toUpperCase());
	}

	@Converter(autoApply = true)
	public static class ConverterImpl implements AttributeConverter<ProxyMode, String> {

		@Override
		public String convertToDatabaseColumn(ProxyMode mode) {
			return mode.name();
		}

		@Override
		public ProxyMode convertToEntityAttribute(String dbData) {
			return ProxyMode.valueOfMode(dbData);
		}
	}
}
