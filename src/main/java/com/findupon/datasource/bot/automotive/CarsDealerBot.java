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

package com.findupon.datasource.bot.automotive;

import com.plainviewrd.datasource.bot.AbstractDealerRetrievalBot;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.IntStream;


public class CarsDealerBot extends AbstractDealerRetrievalBot {

	@Override
	protected void obtainDatasourceUrls() {
		int lastPage = 403;
		JSONArray dealerArray = new JSONArray();
		IntStream.rangeClosed(1, lastPage).forEach(i -> {
			String url = "https://www.cars.com/dealers/buy/90210/?rd=99999&sortBy=DISTANCE&order=ASC&page=" + i + "&perPage=100";
			Document d = com.plainviewrd.commons.netops.ConnectionAgent.INSTANCE.stealthDownload(url).getDocument();
			if(i % (lastPage / 50) == 0) {
				System.err.printf("%.2f%% complete%n", (float)i / lastPage * 100);
			}
			if(d != null) {
				for(Element e : d.getElementsByTag("script")) {
					String script = e.html();
					if(script.startsWith("window.carsDealerSearchApiResponse = ")) {
						script = script.split("\n")[0];
						script = script.substring(script.indexOf("window.carsDealerSearchApiResponse = ") + "window.carsDealerSearchApiResponse = ".length());
						script = script.substring(0, script.length() - 1);
						JSONArray ja = new JSONObject(script).getJSONArray("dealerSummaryList");
						for(int x = 0; x < ja.length(); x++) {
							dealerArray.put(ja.getJSONObject(x));
						}
						break;
					}
				}
			}
		});
		try {
			FileUtils.writeStringToFile(new File("cars-dealers.json"), dealerArray.toString(), StandardCharsets.UTF_8);
		} catch(IOException ioe) {
			ioe.printStackTrace();
		}
		System.err.println("done");
	}

	@Override
	protected String getSourceName() {
		return "CarsDealerBot";
	}

	@Override
	protected boolean verifyAssetType() {
		return false;
	}
}
