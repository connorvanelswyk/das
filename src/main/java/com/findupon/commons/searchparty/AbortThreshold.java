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

package com.findupon.commons.searchparty;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;


final class AbortThreshold implements Serializable {
	private static final long serialVersionUID = -3154033513697379097L;

	private final AbortTimePair[] abortTimePairs;

	AbortThreshold(AbortTimePair... abortTimePairs) {
		this.abortTimePairs = abortTimePairs;
	}

	static AbortThreshold fromStandardRange(int... countThresholds) {
		AbortTimePair[] abortTimePairs = new AbortTimePair[countThresholds.length];
		int currentMinuteRange = 15;
		for(int x = 0; x < countThresholds.length; x++) {
			abortTimePairs[x] = new AbortTimePair(countThresholds[x], currentMinuteRange, TimeUnit.MINUTES);
			currentMinuteRange += 15;
		}
		return new AbortThreshold(abortTimePairs);
	}

	boolean shouldAbort(int currentCount, long currentTime, TimeUnit timeUnit) {
		for(AbortTimePair abortTimePair : this.abortTimePairs) {
			if(timeUnit.toMillis(currentTime) >= abortTimePair.timeThresholdMillis && currentCount < abortTimePair.countThreshold) {
				return true;
			}
		}
		return false;
	}

	static final class AbortTimePair {
		long timeThresholdMillis;
		int countThreshold;

		AbortTimePair(int countThreshold, long timeThreshold, TimeUnit timeUnit) {
			this.countThreshold = countThreshold;
			this.timeThresholdMillis = timeUnit.toMillis(timeThreshold);
		}
	}
}
