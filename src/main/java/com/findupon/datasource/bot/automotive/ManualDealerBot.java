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
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;


@Component
public class ManualDealerBot extends AbstractDealerRetrievalBot {

	@Autowired private JdbcTemplate jdbcTemplate;
	private final String searchUrl = "https://api.cognitive.microsoft.com/bing/v7.0/search?subscription-key=7f6b27a45dc34d7d88a73a84f4a1740d&q=";


	@Override
	protected void obtainDatasourceUrls() {
		String[] exclusions = {"yelp", "google", "kbb", "yellowpages", "autotrader", "www.cars.com", "bbb.org",
				"ripoffreport", "bing", "advisor", "carmax", "carfax", "facebook", "townhall", "usa.com", "www.auto.com",
				".cadillac.com", ".ford.com", "twitter", "instagram", "mapquest", "usdealers.", "cargurus", "iseecars",
				"carstory", "www.lexus.com", ".edu", "www.kia.com", "www.llbean.com", "loopnet", ".org", "yahoo", "news",
				"magazine", "dictionary", "business", "mymms", "beachcalifornia", "zillow", "trulia", "realtor", "youtube",
				"athletic", "www.aetv", "dnd.wizards", "www.jnj", "www.cprogramming", "www.ssactivewear", "roblox",
				"www.indexusedcars", "www.superpages", "www.autoblog", "www.automd", "www.all-autos", "www.dictionary",
				"money.cnn", "themotoring", "www.imdb", "www.localautopoint.net/", "www.amazon", "www.autoguide",
				"www.hm.com", "www.chamberofcommerce", "www.enterprise", "listings.findthecompany", "www.bizapedia",
				"www.roblox", "www.ssww", "www.groupon", "www.jcrew", "en.bitcoin.it/", "www.townkaimuki", "www.dandb",
				"www.buysellautomart", "www.att", "www.whitepages", "www.citysearch", "www.finduslocal", "buysellautomart",
				"moneymorning", "www.newcars", "www.k12", "dealer-network.bankofamerica", "www.eonline", "www.siddillon",
				"www.hertz", "util.automobilemag", "www.jdbyrider", "www.lhmusedcars", "www.getauto", "locations.hendrickauto",
				"reviews.birdeye", "www.myautosource", "www.searchonamerica", "profile.infofree", "www.manta", "www.bestcarfinder",
				"businessfinder.al", "www.angieslist", "www.russellathletic", "www.car-inc", "www.foxmotors", "www.wheels",
				"usplaces", "www.edmunds", "www.avis", "www.rogers", "usedcarsguru", "www.corporationwiki", "www.dreyers",
				"bizstanding", "rundeautogroup", "www.carlotz", "www.all-autos", "www.carsdirect", "cars.oodle", "us.kompass",
				"www.rightway", "www.wholesaleinc", "thisisl", "idioms.thefreedictionary", "www.auto.com", "i18nqa", "realestate",
				"dmv.com", "dmv.org", "rehold.com", "openingtimes", "dexknows", "opendatany", "yellowbot"
		};

		List<ZipDealer> dealers = new ArrayList<>();
		jdbcTemplate.query("select id, name, addr from zip_dealers where url is null", rs -> {
			dealers.add(new ZipDealer(rs.getInt("id"), rs.getString("name"), rs.getString("addr")));
		});
		System.out.println("dealers size: " + dealers.size());

		ExecutorService service = Executors.newFixedThreadPool(50);
		AtomicLong completed = new AtomicLong();

		dealers.forEach(dealer -> service.execute(() -> {
			long c;
			if((c = completed.incrementAndGet()) % 100 == 0) {
				System.out.println(com.plainviewrd.commons.utilities.ConsoleColors.green("progress: " + String.format("%.2f%%", c / (float)dealers.size() * 100)));
			}
			dealer.name = StringUtils.replace(dealer.name, "&", "and");
			dealer.name = com.plainviewrd.commons.searchparty.ScoutServices.pureTextNormalizer(dealer.name, true);



			/* TODO: if you run this again, use different agent, not through proxy so our bandwidth is not wasted */


			Document document = com.plainviewrd.commons.netops.ConnectionAgent.INSTANCE.stealthDownload(searchUrl + com.plainviewrd.commons.searchparty.ScoutServices.encodeSpacing(dealer.name + " " + dealer.address)).getDocument();
			if(document != null) {
				String json = com.plainviewrd.commons.searchparty.ScoutServices.normalize(com.plainviewrd.commons.utilities.JsoupUtils.stripTags(document.html(), "html", "head", "body", "iframe"));

				String[] urls = StringUtils.substringsBetween(json, "\"url\": \"", "\"},");

				if(urls != null) {
					for(String url : urls) {
						url = StringUtils.trimToNull(StringUtils.replace(url, "\\", ""));
						String domain = com.plainviewrd.commons.searchparty.ScoutServices.formUrlFromString(url, true);
						if(domain == null) {
							continue;
						}
						boolean nogo = false;
						for(String ex : exclusions) {
							if(StringUtils.containsIgnoreCase(url, ex)) {
								nogo = true;
								break;
							}
						}
						if(nogo) {
							continue;
						}
						boolean containsany = false;
						for(String split : com.plainviewrd.commons.searchparty.ScoutServices.pureTextNormalizer(dealer.name).split(" ")) {
							if(StringUtils.containsIgnoreCase(domain, split)) {
								containsany = true;
								break;
							}
						}
						if(containsany) {
							dealer.url = domain;
							break;
						}
					}
					if(dealer.url != null) {
						jdbcTemplate.update("update zip_dealers set url = ? where id = ?", dealer.url, dealer.id);
					}
				}
			}
		}));
		service.shutdown();
		try {
			service.awaitTermination(12L, TimeUnit.HOURS);
		} catch(InterruptedException e) {
			Thread.currentThread().interrupt();
			e.printStackTrace();
		}
	}

	@Override
	protected String getSourceName() {
		return "ManualDealerBot";
	}

	@Override
	protected boolean verifyAssetType() {
		return true;
	}
}

class ZipDealer {
	ZipDealer(Integer id, String name, String address) {
		this.id = id;
		this.name = name;
		this.address = address;
	}

	Integer id;
	String name;
	String address;
	String url;
}