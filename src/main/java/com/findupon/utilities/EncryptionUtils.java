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

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.security.NoSuchAlgorithmException;


public final class EncryptionUtils {
	private static final Logger logger = LoggerFactory.getLogger(EncryptionUtils.class);
	private static final Object mutex = new Object();
	private static final String KEY_256 = "xp5cz5oZyq6tJ3e8s4hl43oAWj50giFo";
	private static final Key key;
	private static final Cipher cipher;

	static {
		try {
			key = new SecretKeySpec(Base64.decodeBase64(KEY_256.getBytes()), "AES");
			cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
		} catch(NoSuchAlgorithmException | NoSuchPaddingException e) {
			throw new RuntimeException(e);
		}
	}

	public static String encrypt(String message) {
		synchronized(mutex) {
			try {
				cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(new byte[cipher.getBlockSize()]));
				return Base64.encodeBase64String(cipher.doFinal(message.getBytes()));
			} catch(Exception e) {
				logger.error("Could not encrypt cluster message! Raw message: \n{}", message, e);
			}
			return null;
		}
	}

	public static String decrypt(String message) {
		synchronized(mutex) {
			try {
				cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(new byte[cipher.getBlockSize()]));
				return new String(cipher.doFinal(Base64.decodeBase64(message)));
			} catch(Exception e) {
				logger.error("Could not decrypt cluster message! Raw message: \n{}", message, e);
			}
			return null;
		}
	}
}
