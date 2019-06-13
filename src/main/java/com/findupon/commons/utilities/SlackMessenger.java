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

import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


@Component
public class SlackMessenger {
	private static final Logger logger = LoggerFactory.getLogger(SlackMessenger.class);

	private static final String ENDPOINT_MESSAGE = "chat.postMessage";
	private static final String ENDPOINT_FILE_UPLOAD = "files.upload";

	@Value("${slack.enabled}") private Boolean enabled;
	@Value("${slack.bot_token}") private String token;
	@Value("${slack.user_name}") private String userName;
	@Value("${slack.default_channel}") private String defaultChannel;


	public void sendMessage(String message) {
		logger.info(message.replace("*", "").replace("`", "").replace("_", ""));
		post(ENDPOINT_MESSAGE, messageParams(message, defaultChannel));
	}

	public void sendMessageWithArgs(String format, Object... args) {
		String message = String.format(format, args);
		sendMessage(message);
	}

	public void sendTextFile(String title, String text) {
		post(ENDPOINT_FILE_UPLOAD, uploadParams(title, text, defaultChannel));
	}

	private void post(String apiEndpoint, List<NameValuePair> requestParams) {
		if(!enabled) {
			return;
		}
		CloseableHttpClient client = HttpClients.createDefault();
		CloseableHttpResponse response;
		try {
			HttpPost httpPost = new HttpPost("https://slack.com/api/" + apiEndpoint);
			List<NameValuePair> params = new ArrayList<>(baseParams());
			params.addAll(requestParams);
			httpPost.setEntity(new UrlEncodedFormEntity(params));
			response = client.execute(httpPost);
			String responseStr = EntityUtils.toString(response.getEntity());
			logger.trace("[SlackMessenger] - API response ({}): {}" + response.getStatusLine().getStatusCode(), responseStr);
		} catch(Exception e) {
			logger.warn("[SlackMessenger] - Error posting message", e);
		} finally {
			try {
				client.close();
			} catch(IOException e) {
				logger.warn("[SlackMessenger] - Error closing http client", e);
			}
		}
	}

	private List<NameValuePair> baseParams() {
		List<NameValuePair> params = new ArrayList<>();
		params.add(new BasicNameValuePair("id", "1"));
		params.add(new BasicNameValuePair("token", token));
		params.add(new BasicNameValuePair("username", userName));
		params.add(new BasicNameValuePair("as_user", "true"));
		return params;
	}

	private List<NameValuePair> messageParams(String message, String channel) {
		List<NameValuePair> params = new ArrayList<>();
		params.add(new BasicNameValuePair("type", "message"));
		params.add(new BasicNameValuePair("channel", channel));
		params.add(new BasicNameValuePair("text", message));
		params.add(new BasicNameValuePair("mrkdwn", "true"));
		return params;
	}

	private List<NameValuePair> uploadParams(String title, String text, String channel) {
		List<NameValuePair> params = new ArrayList<>();
		params.add(new BasicNameValuePair("channels", channel));
		params.add(new BasicNameValuePair("title", title));
		params.add(new BasicNameValuePair("content", text));
		params.add(new BasicNameValuePair("mimetype", "text\\/plain"));
		params.add(new BasicNameValuePair("filetype", "text"));
		params.add(new BasicNameValuePair("pretty_type", "Text"));
		params.add(new BasicNameValuePair("mode", "hosted"));
		params.add(new BasicNameValuePair("editable", "false"));
		params.add(new BasicNameValuePair("is_external", "false"));
		params.add(new BasicNameValuePair("external_type", ""));
		return params;
	}
}
