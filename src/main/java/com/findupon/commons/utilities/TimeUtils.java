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

import com.google.common.base.Stopwatch;

import java.util.concurrent.TimeUnit;


public final class TimeUtils {

	private static final long defaultConditionalMillis = 1000L;

	public static String format(long millis) {
		return String.format("%d hr %d min %d sec", millis / (3600 * 1000), millis / (60 * 1000) % 60, millis / 1000 % 60);
	}

	public static String format(Stopwatch stopwatch) {
		return format(stopwatch.elapsed(TimeUnit.MILLISECONDS));
	}

	public static String formatSeconds(long millis) {
		return String.format("%.3f", (float)millis / 1000F);
	}

	public static String formatSeconds(Stopwatch stopwatch) {
		return formatSeconds(stopwatch.elapsed(TimeUnit.MILLISECONDS));
	}

	public static String formatConditionalSeconds(Stopwatch stopwatch) {
		return formatConditionalSeconds(stopwatch.elapsed(TimeUnit.MILLISECONDS), defaultConditionalMillis);
	}

	public static String formatConditionalSeconds(long millis) {
		return formatConditionalSeconds(millis, defaultConditionalMillis);
	}

	public static String formatConditionalSeconds(Stopwatch stopwatch, long thresholdMillis) {
		return formatConditionalSeconds(stopwatch.elapsed(TimeUnit.MILLISECONDS), thresholdMillis);
	}

	public static String formatConditionalSeconds(long millis, long thresholdMillis) {
		String queryTimeStr = formatSeconds(millis);
		if(millis < thresholdMillis) {
			return ConsoleColors.green(queryTimeStr);
		} else {
			return ConsoleColors.red(queryTimeStr);
		}
	}
}
