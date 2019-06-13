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

package com.findupon.commons.utilities;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A suite of json helper methods we can trust to provide predictable outcomes.
 * This circumvents having to translate between JSONObject.NULL & normal null
 * and make use of opts that return null instead of primitive default values.
 * <p>
 * Use the opt methods to return null if missing or JSONObject.NULL
 * Use the get methods to throw an exception if failed
 */
public final class JsonUtils {
	private static final Logger logger = LoggerFactory.getLogger(JsonUtils.class);

	public static void putObject(JSONObject jo, String key, Object value) {
		if(key == null) {
			logger.warn("Null key not allowed");
			return;
		}
		try {
			jo.put(key, value == null ? JSONObject.NULL : value);
		} catch(JSONException e) {
			logger.error("Could not place key", e);
		}
	}

	public static String optString(JSONObject jo, String key) {
		return StringUtils.defaultIfEmpty(jo.optString(key), null);
	}

	public static Integer optInteger(JSONObject jo, String key) {
		if(jo.has(key)) {
			Object object;
			try {
				object = jo.get(key);
			} catch(JSONException e) {
				return null;
			}
			if(object == null || object == JSONObject.NULL) {
				return null;
			}
			if(object instanceof Number) {
				try {
					return ((Number)object).intValue();
				} catch(Exception e) {
					return null;
				}
			} else if(object instanceof String) {
				try {
					return Integer.parseInt((String)object);
				} catch(Exception e) {
					return null;
				}
			}
		}
		return null;
	}

	public static Long optLong(JSONObject jo, String key) {
		if(jo.has(key)) {
			Object object;
			try {
				object = jo.get(key);
			} catch(JSONException e) {
				return null;
			}
			if(object == null || object == JSONObject.NULL) {
				return null;
			}
			if(object instanceof Number) {
				try {
					return ((Number)object).longValue();
				} catch(Exception e) {
					return null;
				}
			} else if(object instanceof String) {
				try {
					return Long.parseLong((String)object);
				} catch(Exception e) {
					return null;
				}
			}
		}
		return null;
	}

	public static Boolean optBoolean(JSONObject jo, String key) {
		try {
			return jo.getBoolean(key);
		} catch(JSONException e) {
			return null;
		}
	}

	public static String getString(JSONObject jo, String key) throws JSONException {
		String value = optString(jo, key);
		if(value == null) {
			throw new JSONException("Key [" + key + "] came back null!");
		}
		return value;
	}

	public static Integer getInteger(JSONObject jo, String key) throws JSONException {
		Integer value = optInteger(jo, key);
		if(value == null) {
			throw new JSONException("Key [" + key + "] came back null!");
		}
		return value;
	}

	public static Long getLong(JSONObject jo, String key) throws JSONException {
		Long value = optLong(jo, key);
		if(value == null) {
			throw new JSONException("Key [" + key + "] came back null!");
		}
		return value;
	}

	public static Boolean getBoolean(JSONObject jo, String key) throws JSONException {
		Boolean value = optBoolean(jo, key);
		if(value == null) {
			throw new JSONException("Key [" + key + "] came back null!");
		}
		return value;
	}
}
