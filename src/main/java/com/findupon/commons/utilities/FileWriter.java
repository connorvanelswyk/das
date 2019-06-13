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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;


public class FileWriter {
	private static final Logger logger = LoggerFactory.getLogger(FileWriter.class);

	public static boolean write(String path, String content) {
		try {
			Files.write(Paths.get(path), content.getBytes(), StandardOpenOption.CREATE);
		} catch(IOException e) {
			logger.error("Error writing to file [{}]", path, e);
			return false;
		}
		return true;
	}

	public static boolean append(String path, String content) {
		try {
			Files.write(Paths.get(path), content.getBytes(), StandardOpenOption.APPEND, StandardOpenOption.CREATE);
		} catch(IOException e) {
			logger.error("Error writing to file [{}]", path, e);
			return false;
		}
		return true;
	}
}
