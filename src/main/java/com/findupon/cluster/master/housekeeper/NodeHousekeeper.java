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

package com.findupon.cluster.master.housekeeper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;


public abstract class NodeHousekeeper implements Runnable {
	protected final Logger logger = LoggerFactory.getLogger(getClass());
	protected final AtomicBoolean run = new AtomicBoolean(true);

	protected abstract long getTimeoutInterval();

	public synchronized void shutdown() {
		run.set(false);
	}

	protected boolean sleep() {
		try {
			Thread.sleep(getTimeoutInterval());
		} catch(InterruptedException e) {
			Thread.currentThread().interrupt();
			logger.warn("[NodeHousekeeper] - Thread interrupted during sleep!", e);
			return false;
		}
		return run.get();
	}
}
