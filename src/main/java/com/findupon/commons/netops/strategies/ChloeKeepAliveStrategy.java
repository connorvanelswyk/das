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

import com.findupon.commons.netops.ContextOps;
import org.apache.http.HeaderElement;
import org.apache.http.HeaderElementIterator;
import org.apache.http.HttpResponse;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.message.BasicHeaderElementIterator;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Decides how long a connection is allowed to be idle in the pool.
 * If the connection is idle for longer than allowed, it will not be reused.
 * <p>
 * If the Keep-Alive header is not present in the response, HttpClient assumes the connection can be kept alive indefinitely.
 * ChloeKeepAliveStrategy circumvents this with the ability to manage dead connections.
 */
public class ChloeKeepAliveStrategy implements ConnectionKeepAliveStrategy {
	public static final ChloeKeepAliveStrategy INSTANCE = new ChloeKeepAliveStrategy();
	private static final Logger logger = LoggerFactory.getLogger(ChloeKeepAliveStrategy.class);
	private static final int defaultKeepAlive = 10 * 1000;


	@Override
	public long getKeepAliveDuration(HttpResponse response, HttpContext context) {
		HeaderElementIterator it = new BasicHeaderElementIterator(response.headerIterator(HTTP.CONN_KEEP_ALIVE));
		while(it.hasNext()) {
			HeaderElement headerElement = it.nextElement();
			String param = headerElement.getName();
			String value = headerElement.getValue();
			if(value != null && param.equalsIgnoreCase("timeout")) {
				try {
					return Long.parseLong(value) * 1000;
				} catch(NumberFormatException e) {
					logger.trace("{}[ChloeKeepAliveStrategy] - Invalid keep alive timeout header [{}]",
							ContextOps.nodePre(context), value);
				}
			}
		}
		return defaultKeepAlive;
	}
}
