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

import com.findupon.utilities.PropertyLoader;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.SocketUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URL;


public final class NetworkUtils {
	private static final Logger logger = LoggerFactory.getLogger(NetworkUtils.class);
	private static final int minPort = 60328;
	private static final int maxPort = 60591;
	private static String externalIp;


	public static String getExternalOrLocalIpAddress() {
		boolean production = PropertyLoader.getBoolean("production");
		String ip;
		try {
			if(production) {
				ip = getExternalIpInternal();
			} else {
				ip = InetAddress.getLocalHost().getHostAddress();
			}
		} catch(IOException e) {
			throw new RuntimeException("Unable to determine external IP", e);
		}
		logger.debug("[NetworkUtils] - Determined IP address for env [{}]: {}", production ? "prod" : "dev", ip);
		return ip;
	}

	public static String getExternalIp() {
		try {
			return getExternalIpInternal();
		} catch(IOException e) {
			logger.error("[NetworkUtils] - Could not determine external IP. Error: [{}]", ExceptionUtils.getRootCauseMessage(e));
			return null;
		}
	}

	public static int getAvailablePort() {
		try(ServerSocket portFinder = new ServerSocket(SocketUtils.findAvailableTcpPort(minPort, maxPort))) {
			return portFinder.getLocalPort();
		} catch(IOException e) {
			throw new RuntimeException("Unable to dynamically obtain server port");
		}
	}

	private static synchronized String getExternalIpInternal() throws IOException {
		if(externalIp == null) {
			URL checkIp = new URL("http://checkip.amazonaws.com");
			try(BufferedReader reader = new BufferedReader(new InputStreamReader(checkIp.openStream()))) {
				externalIp = reader.readLine();
			}
		}
		return externalIp;
	}
}
