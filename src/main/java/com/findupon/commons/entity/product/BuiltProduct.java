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

package com.findupon.commons.entity.product;

import java.io.Serializable;


public class BuiltProduct implements Serializable {
	private static final long serialVersionUID = -7281773613530939116L;

	private Product product;
	private String listingId;
	private boolean markedForRemoval;


	public static BuiltProduct success(Product product) {
		BuiltProduct builtProduct = new BuiltProduct();
		builtProduct.setProduct(product);
		return builtProduct;
	}

	public static BuiltProduct removed(String listingId) {
		BuiltProduct builtProduct = new BuiltProduct();
		builtProduct.setListingId(listingId);
		builtProduct.setMarkedForRemoval(true);
		return builtProduct;
	}

	public Product getProduct() {
		return product;
	}

	public void setProduct(Product product) {
		this.product = product;
	}

	public String getListingId() {
		return listingId;
	}

	public void setListingId(String listingId) {
		this.listingId = listingId;
	}

	public boolean isMarkedForRemoval() {
		return markedForRemoval;
	}

	public void setMarkedForRemoval(boolean markedForRemoval) {
		this.markedForRemoval = markedForRemoval;
	}
}
