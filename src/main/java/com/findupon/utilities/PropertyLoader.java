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

package com.findupon.utilities;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;


public final class PropertyLoader {
	private static final Logger logger = LoggerFactory.getLogger(PropertyLoader.class);
	private static final Properties properties = new Properties();
	private static final String path = "/application.properties";


	static {
		try(InputStream inputStream = PropertyLoader.class.getResourceAsStream(path)) {
			if(inputStream == null) {
				logger.error("[PropertyLoader] - Null input stream loading from [{}]", path);
			} else {
				properties.load(inputStream);
			}
		} catch(IOException e) {
			throw new RuntimeException("Could not load application.properties", e);
		}
	}

	public static boolean defaultBoolean(String key) {
		Boolean value = optBoolean(key);
		if(value == null) {
			value = false;
		}
		return value;
	}

	public static boolean getBoolean(String key) {
		Boolean value = optBoolean(key);
		if(value == null) {
			throw new IllegalStateException("Null property for key " + key);
		}
		return value;
	}

	public static Boolean optBoolean(String key) {
		String value = getString(key);
		if(value == null) {
			return null;
		}
		if(value.equalsIgnoreCase("true")) {
			return true;
		}
		if(value.equalsIgnoreCase("false")) {
			return false;
		}
		logger.warn("[PropertyLoader] - Property [{}] is not a boolean", key);
		return null;
	}

	public static int defaultInteger(String key) {
		Integer value = optInteger(key);
		if(value == null) {
			value = -1;
		}
		return value;
	}

	public static int getInteger(String key) {
		Integer value = optInteger(key);
		if(value == null) {
			throw new IllegalStateException("Null property for key " + key);
		}
		return value;
	}

	public static Integer optInteger(String key) {
		String value = properties.getProperty(key, null);
		try {
			return Integer.parseInt(value);
		} catch(NumberFormatException e) {
			return null;
		}
	}

	public static String defaultString(String key) {
		String value = optString(key);
		if(value == null) {
			value = StringUtils.EMPTY;
		}
		return value;
	}

	public static String getString(String key) {
		String value = optString(key);
		if(value == null) {
			throw new IllegalStateException("Null property for key " + key);
		}
		return value;
	}

	public static String optString(String key) {
		return properties.getProperty(key, null);
	}

	public static Object optObject(String key) {
		return properties.getOrDefault(key, null);
	}

	public static Object getObject(String key) {
		Object value = optObject(key);
		if(value == null) {
			throw new IllegalStateException("Null property for key " + key);
		}
		return value;
	}
}
