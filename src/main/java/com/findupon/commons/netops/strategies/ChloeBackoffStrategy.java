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

package com.findupon.commons.netops.strategies;

import com.findupon.commons.netops.entity.HttpStatusCode;
import org.apache.http.HttpResponse;
import org.apache.http.client.ConnectionBackoffStrategy;

import java.net.ConnectException;
import java.net.SocketTimeoutException;


public class ChloeBackoffStrategy implements ConnectionBackoffStrategy {
	public static final ChloeBackoffStrategy INSTANCE = new ChloeBackoffStrategy();

	@Override
	public boolean shouldBackoff(Throwable t) {
		return t instanceof SocketTimeoutException || t instanceof ConnectException;
	}

	@Override
	public boolean shouldBackoff(HttpResponse response) {
		return HttpStatusCode.isBackoffCode(response.getStatusLine().getStatusCode());
	}
}
