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
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;


public class VultrStats {

	private final Map<String, LongAdder> usageByMonth = new LinkedHashMap<>();


	public static void main(String... args) {
		new VultrStats().getVultrStats();
	}

	private void getVultrStats() {
		JSONObject serverListJo = getVultrJson("https://api.vultr.com/v1/server/list");
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
		Calendar currentMonth = Calendar.getInstance();

		if(serverListJo != null) {
			List<VultrNode> nodes = serverListJo.toMap().entrySet().stream()
					.filter(Map.Entry.class::isInstance)
					.map(Map.Entry.class::cast)
					.map(Map.Entry::getValue)
					.filter(HashMap.class::isInstance)
					.map(HashMap.class::cast)
					.map(m -> new VultrNode((String)m.get("SUBID"), (String)m.get("location"), (String)m.get("main_ip")))
					.collect(Collectors.toList());

			long totalBytes = 0;
			long thisMonthsTotalBytes = 0;

			for(VultrNode node : nodes) {
				try {
					// they limit their API to 2 requests a second
					Thread.sleep(600);
				} catch(InterruptedException e) {
					Thread.currentThread().interrupt();
					e.printStackTrace();
				}
				JSONObject bandwidthJo = getVultrJson("https://api.vultr.com/v1/server/bandwidth?SUBID=" + node.subId);
				if(bandwidthJo != null) {
					JSONArray incomingJo = (JSONArray)bandwidthJo.get("incoming_bytes");
					long totalBytesPerNode = 0;
					long currentMonthTotalBytesPerNode = 0;

					// usage by days: "2014-06-10", "81072581"
					for(int x = 0; x < incomingJo.length(); x++) {
						incomingJo.get(x);
						JSONArray dayJo = (JSONArray)incomingJo.get(x);
						if(dayJo.length() == 2) {
							String bytesStr = dayJo.getString(1);

							if(NumberUtils.isDigits(bytesStr)) {
								long bytes = Long.parseLong(bytesStr);
								totalBytesPerNode += Long.parseLong(bytesStr);
								String dateStr = dayJo.getString(0);

								if(StringUtils.isNotBlank(dateStr) && dateStr.contains("-")) {
									// get past 30 days usage
									Date current;
									try {
										current = format.parse(dateStr);
									} catch(ParseException e) {
										e.printStackTrace();
										continue;
									}
									Calendar usageCal = Calendar.getInstance();
									usageCal.setTime(current);
									// same month and year as currently
									if(currentMonth.get(Calendar.YEAR) == usageCal.get(Calendar.YEAR)
											&& currentMonth.get(Calendar.MONTH) == usageCal.get(Calendar.MONTH)) {
										currentMonthTotalBytesPerNode += bytes;
									}
									// add to the total
									String month = dateStr.substring(0, dateStr.lastIndexOf("-"));
									usageByMonth.computeIfAbsent(month, b -> new LongAdder()).add(bytes);
								} else {
									System.err.printf("Invalid date string [%s] from subid [%s]", dateStr, node.subId);
								}
							} else {
								System.err.printf("Non-number usage bytes [%s]", bytesStr);
							}
						} else {
							System.err.printf("Invalid json usage by day [%s]", dayJo.toString());
						}
					}
					node.totalUsage = MemoryUtils.format(totalBytesPerNode);
					node.currentMonthUsage = MemoryUtils.format(currentMonthTotalBytesPerNode);
					totalBytes += totalBytesPerNode;
					thisMonthsTotalBytes += currentMonthTotalBytesPerNode;
				}
			}
			nodes.sort(Comparator.comparing(n -> n.location));
			nodes.forEach(n -> System.out.printf("SubID [%s]  Current Month's Usage [%s]  Total Usage [%s]  Location [%s]  IP [%s]\n",
					n.subId,
					(n.currentMonthUsage.contains("T") ? com.findupon.commons.utilities.ConsoleColors.red(n.currentMonthUsage) : com.findupon.commons.utilities.ConsoleColors.green(n.currentMonthUsage)),
					com.findupon.commons.utilities.ConsoleColors.cyan(n.totalUsage), n.location, n.ip));

			System.out.println("\n\nUsage by Month\n");
			usageByMonth.forEach((k, v) -> System.out.printf("%s:  %s\n", k, com.findupon.commons.utilities.ConsoleColors.cyan(MemoryUtils.format(v.longValue()))));

			System.out.println("\n\nTotal Usage\n");
			System.out.printf("This month:  %s\n", com.findupon.commons.utilities.ConsoleColors.green(MemoryUtils.format(thisMonthsTotalBytes)));
			System.out.printf("All-time:    %s\n\n\n", com.findupon.commons.utilities.ConsoleColors.cyan(MemoryUtils.format(totalBytes)));
		}
	}

	private JSONObject getVultrJson(String url) {
		String key = "RNPF3WJYTFWF6C5FCBRLXZAFKZTLSOBU43CA";
		HttpClient httpclient = HttpClientBuilder.create().build();
		HttpGet request = new HttpGet(url);
		request.addHeader("API-Key", key);
		String content = null;
		try {
			HttpResponse response = httpclient.execute(request);
			content = EntityUtils.toString(response.getEntity());
			return new JSONObject(content);
		} catch(Exception e) {
			System.out.println("content: " + content);
			e.printStackTrace();
			return null;
		}
	}

	private class VultrNode {
		String subId;
		String location;
		String ip;
		String totalUsage;
		String currentMonthUsage;

		VultrNode(String subId, String location, String ip) {
			this.subId = subId;
			this.location = location;
			this.ip = ip;
		}
	}
}
