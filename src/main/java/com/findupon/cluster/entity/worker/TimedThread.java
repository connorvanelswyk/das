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

package com.findupon.cluster.entity.worker;


public class TimedThread {
	private final Thread thread;
	private final long startTime;


	public TimedThread(Thread thread) {
		this.thread = thread;
		this.startTime = System.currentTimeMillis();
	}

	public Thread getThread() {
		return thread;
	}

	public long getStartTime() {
		return startTime;
	}
}
