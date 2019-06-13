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


public final class MemoryUtils {

	public static String getCurrentHeapSizeStr() {
		return format(getCurrentHeapSize());
	}

	public static String getMaxHeapSizeStr() {
		return format(getMaxHeapSize());
	}

	public static long getCurrentHeapSize() {
		return Runtime.getRuntime().totalMemory();
	}

	public static long getMaxHeapSize() {
		return Runtime.getRuntime().maxMemory();
	}

	public static String format(long bytes) {
		if(bytes < 1024) {
			return bytes + " B";
		}
		int x = (63 - Long.numberOfLeadingZeros(bytes)) / 10;
		return String.format("%.1f %sB", (double)bytes / (1L << (x * 10)), " KMGTPE".charAt(x));
	}
}
