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

package com.findupon.datasource.management;

import com.plainviewrd.datasource.bot.AbstractDealerRetrievalBot;
import com.plainviewrd.datasource.bot.AbstractImportProcess;
import com.plainviewrd.datasource.bot.aircraft.FaaImportProcess;
import com.plainviewrd.datasource.bot.automotive.CarsDirectProcess;
import com.plainviewrd.datasource.bot.automotive.IntrospectiveDealerRetrievalBot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


/**
 * Note that these are scheduled from the xml application context as we only want them running from the master node.
 */
@Component
public class SchedulingManager {
	private static final Logger logger = LoggerFactory.getLogger(SchedulingManager.class);

	@Value("${production}") private Boolean production;


	public void dealerManagerProcess() {
		if(!production) {
			return;
		}
		logger.info("[SchedulingManager] - Queueing introspective dealer retrieval process to run...");
		AbstractDealerRetrievalBot dealerRetrievalBot = new IntrospectiveDealerRetrievalBot();
		com.plainviewrd.commons.utilities.SpringUtils.autowire(dealerRetrievalBot);
		dealerRetrievalBot.run();
	}

	public void carsDirectLoadProcess() {
		runProcess(new CarsDirectProcess());
	}

	public void faaLoadProcess() {
		runProcess(new FaaImportProcess());
	}

	private void runProcess(AbstractImportProcess importProcess) {
		if(!production) {
			return;
		}
		logger.info("[SchedulingManager] - Queueing {} for run...", importProcess.getClass().getSimpleName());
		com.plainviewrd.commons.utilities.SpringUtils.autowire(importProcess);
		importProcess.init();
	}
}
