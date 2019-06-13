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

package com.findupon.parsing;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;


public final class ImageColorRecognition {

	public static void main(String... args) {
		File file = new File("/Users/ciminelli/Desktop/car.jpg");
		ImageInputStream is;
		try {
			is = ImageIO.createImageInputStream(file);
		} catch(IOException e) {
			e.printStackTrace();
			return;
		}
		Iterator readers = ImageIO.getImageReaders(is);
		if(!readers.hasNext()) {
			System.out.println("Cannot load the specified file " + file);
			return;
		}
		ImageReader imageReader = (ImageReader)readers.next();
		imageReader.setInput(is);
		BufferedImage image;
		try {
			image = imageReader.read(0);
		} catch(IOException e) {
			e.printStackTrace();
			return;
		}
		int height = image.getHeight();
		int width = image.getWidth();
		Map<Integer, Integer> m = new HashMap<>();
		for(int i = 0; i < width; i++) {
			for(int j = 0; j < height; j++) {
				int rgb = image.getRGB(i, j);
				int[] rgbArr = getRGBArr(rgb);
				if(!isGray(rgbArr)) {
					Integer counter = m.get(rgb);
					if(counter == null) {
						counter = 0;
					}
					counter++;
					m.put(rgb, counter);
				}
			}
		}
		String hexColor = getMostCommonColor(m);
		System.out.println(hexColor);
	}

	@SuppressWarnings("unchecked")
	private static String getMostCommonColor(Map map) {
		List list = new LinkedList(map.entrySet());
		list.sort((o1, o2) -> ((Comparable)((Map.Entry)(o1)).getValue()).compareTo(((Map.Entry)(o2)).getValue()));
		Map.Entry e = (Map.Entry)list.get(list.size() - 1);
		int[] rgb = getRGBArr((Integer)e.getKey());
		return String.format("%s %s %s", Integer.toHexString(rgb[0]), Integer.toHexString(rgb[1]), Integer.toHexString(rgb[2]));
	}

	private static int[] getRGBArr(int pixel) {
		// int alpha = (pixel >> 24) & 0xff;
		int r = (pixel >> 16) & 0xff;
		int g = (pixel >> 8) & 0xff;
		int b = (pixel) & 0xff;
		return new int[]{r, g, b};
	}

	private static boolean isGray(int[] rgbArr) {
		int rgDiff = rgbArr[0] - rgbArr[1];
		int rbDiff = rgbArr[0] - rgbArr[2];
		int tolerance = 10;
		return rgDiff <= tolerance && rgDiff >= -tolerance || rbDiff <= tolerance && rbDiff >= -tolerance;
	}
}
