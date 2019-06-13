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

package com.findupon.commons.building;

import com.findupon.commons.bot.automotive.ListingAutomobileBot;
import com.findupon.commons.dao.AircraftDao;
import com.findupon.commons.dao.AutomobileDao;
import com.findupon.commons.dao.RealEstateDao;
import com.findupon.commons.dao.WatercraftDao;
import com.findupon.commons.entity.datasource.DataSource;
import com.findupon.commons.entity.product.AggregationColumn;
import com.findupon.commons.entity.product.BuiltProduct;
import com.findupon.commons.entity.product.Product;
import com.findupon.commons.entity.product.aircraft.Aircraft;
import com.findupon.commons.entity.product.automotive.Automobile;
import com.findupon.commons.entity.product.realestate.RealEstate;
import com.findupon.commons.entity.product.watercraft.Watercraft;
import com.findupon.commons.netops.ConnectionAgent;
import com.findupon.commons.repository.datasource.DataSourceRepo;
import com.findupon.commons.searchparty.AutomotiveGatherer;
import com.findupon.commons.utilities.ConsoleColors;
import com.findupon.commons.utilities.SpringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.validator.routines.UrlValidator;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


@Component
public class ProductUtils {
	private static final Logger logger = LoggerFactory.getLogger(ProductUtils.class);
	private static final String chloe = "Chloe";
	private static final long productRefreshThresholdMillis = TimeUnit.HOURS.toMillis(4);

	@Autowired private AutomobileDao automobileDao;
	@Autowired private RealEstateDao realEstateDao;
	@Autowired private WatercraftDao watercraftDao;
	@Autowired private AircraftDao aircraftDao;
	@Autowired private DataSourceRepo dataSourceRepo;
	@Autowired private AutomotiveGatherer automotiveGatherer; // more generification needed


	public void mergeExistingProductAndRefreshAggregates(Product product) {
		Objects.requireNonNull(product, "Cannot merge null product!");

		Optional<?> opt = Optional.empty();
		if(product instanceof Automobile) {
			opt = automobileDao.find((Automobile)product);
		} else if(product instanceof RealEstate) {
			opt = realEstateDao.find((RealEstate)product);
		} else if(product instanceof Watercraft) {
			opt = watercraftDao.find((Watercraft)product);
		} else if(product instanceof Aircraft) {
			opt = aircraftDao.find((Aircraft)product);
		}
		Date now = new Date();
		product.setVisitedBy(chloe);
		product.setVisitedDate(now);

		if(opt.isPresent()) {
			mergeExistingProductAndRefreshAggregates(product, (Product)opt.get(), now);
		} else {
			product.setCreationDate(now);
			product.setCreatedBy(chloe);
		}
	}

	public void mergeExistingProductAndRefreshAggregates(Product product, Product existing, Date now) {
		Objects.requireNonNull(product, "Cannot merge null product!");
		Objects.requireNonNull(existing, "Cannot merge null existing product!");
		Objects.requireNonNull(now, "Now date must not be null");

		if(product instanceof Automobile) {
			automotiveGatherer.setAdditionalAttributes((Automobile)product);
		}
		// if we already have an image and didn't pick a new one up, use the existing one
		if(existing.getMainImageUrl() != null && product.getMainImageUrl() == null) {
			product.setMainImageUrl(existing.getMainImageUrl());
		}
		// set the ID so the data service will merge it
		product.setId(existing.getId());
		product.setCreationDate(existing.getCreationDate());
		product.setCreatedBy(existing.getCreatedBy());
		if(product.getVisitedBy() == null) {
			product.setVisitedBy(chloe);
		}
		if(product.getVisitedDate() == null) {
			product.setVisitedDate(now);
		}
		if(setModifiedIfPriceChange(product, existing, now)) {
			logger.debug("[ProductUtils] - Refreshing aggregates for {} based on price change [{}]", product.getClass().getSimpleName(), product.getUrl());
			refreshAggregates(product);
		}
	}

	@SuppressWarnings("unchecked")
	public <P extends Product & Serializable> List<P> findExistingProducts(DataSource dataSource, String listingId) {
		if(dataSource == null || dataSource.getUrl() == null || listingId == null) {
			logger.warn("[ProductUtils] - Missing data source or listing ID for product lookup");
			return new ArrayList<>();
		}
		switch(dataSource.getAssetType()) {
			case AUTOMOBILE:
				return (List<P>)automobileDao.findByDataSourceIdAndListingId(dataSource.getId(), listingId);
			case REAL_ESTATE:
				return (List<P>)realEstateDao.findByDataSourceIdAndListingId(dataSource.getId(), listingId);
			case WATERCRAFT:
				return (List<P>)watercraftDao.findByDataSourceIdAndListingId(dataSource.getId(), listingId);
			case AIRCRAFT:
				return (List<P>)aircraftDao.findByDataSourceIdAndListingId(dataSource.getId(), listingId);
			default:
				throw new UnsupportedOperationException("Bad listing source asset type for find");
		}
	}

	public <P extends Product & Serializable> void saveAll(List<P> products) {
		if(products.isEmpty()) {
			return;
		}
		Class<? extends Product> type = products.get(0).getClass();
		if(Automobile.class.equals(type)) {
			automobileDao.saveAll(products.stream().map(Automobile.class::cast).collect(Collectors.toList()));
		} else if(RealEstate.class.equals(type)) {
			realEstateDao.saveAll(products.stream().map(RealEstate.class::cast).collect(Collectors.toList()));
		} else if(Watercraft.class.equals(type)) {
			watercraftDao.saveAll(products.stream().map(Watercraft.class::cast).collect(Collectors.toList()));
		} else if(Aircraft.class.equals(type)) {
			aircraftDao.saveAll(products.stream().map(Aircraft.class::cast).collect(Collectors.toList()));
		}
	}

	public void removeProductsById(Class<? extends Product> type, List<Long> ids) {
		if(Automobile.class.equals(type)) {
			automobileDao.deleteAllById(ids);
		} else if(RealEstate.class.equals(type)) {
			realEstateDao.deleteAllById(ids);
		} else if(Watercraft.class.equals(type)) {
			watercraftDao.deleteAllById(ids);
		} else if(Aircraft.class.equals(type)) {
			aircraftDao.deleteAllById(ids);
		}
	}

	public void removeProductAndRefreshAggregates(Product product) {
		if(refreshAggregates(product)) {
			if(product instanceof Automobile) {
				automobileDao.delete((Automobile)product);
			} else if(product instanceof RealEstate) {
				realEstateDao.delete((RealEstate)product);
			} else if(product instanceof Watercraft) {
				watercraftDao.delete((Watercraft)product);
			} else if(product instanceof Aircraft) {
				aircraftDao.delete((Aircraft)product);
			}
		}
	}

	/**
	 * @return {@code true} if the product exists, {@code} false otherwise.
	 */
	@SuppressWarnings("unchecked")
	private boolean refreshAggregates(Product product) {
		Objects.requireNonNull(product, "Cannot refresh aggregates on null product!");
		if(product instanceof Automobile) {
			String vin = getAggregationColumnValue(product);
			if(vin == null) {
				return false; // already reported
			}
			List<Automobile> all = automobileDao.findByVin(vin);
			boolean productStillExists = all.stream().anyMatch(a -> {
				if(product.getId() != null) {
					return Objects.equals(product.getId(), a.getId());
				} else {
					return Objects.equals(product.getUrl(), a.getUrl());
				}
			});
			if(!productStillExists) {
				String attr = product.getId() != null ? String.valueOf(product.getId()) : product.getUrl();
				logger.debug("[ProductUtils] - {} [{}] removed, most likely aggregation refresh from another worker. Not refreshing aggregates.",
						product.getClass().getSimpleName(), attr);
				return false;
			}
			List<Automobile> aggregates = all.stream()
					.filter(a -> {
						// don't refresh the product passed in. duh.
						if(product.getId() != null) {
							return !Objects.equals(product.getId(), a.getId());
						} else {
							return !Objects.equals(product.getUrl(), a.getUrl());
						}
					}).collect(Collectors.toList());
			if(aggregates.isEmpty()) {
				logger.debug("[ProductUtils] - No aggregates to refresh for VIN [{}]", vin);
				return true;
			}
			logger.debug("[ProductUtils] - Refreshing [{}] aggregate product{} for VIN [{}]", aggregates.size(), aggregates.size() > 1 ? "s" : "", vin);
			int numRemoved = 0, numNotTouched = 0;
			List<Automobile> refreshed = new ArrayList<>();
			for(Automobile automobile : aggregates) {
				if(automobile.getVisitedDate() != null && automobile.getVisitedDate().after(refreshThreshold())) {
					numNotTouched++;
					continue;
				}
				if(automobile.getDataSourceId() == null) {
					logger.error("[ProductUtils] - Product missing data source ID! Product ID [{}]", automobile.getId());
				}
				Optional<DataSource> opt = dataSourceRepo.findById(automobile.getDataSourceId());
				DataSource dataSource;
				if(opt.isPresent()) {
					dataSource = opt.get();
					if(dataSource.getDataSourceType() == null) {
						logger.error("[ProductUtils] - Null data source type not allowed. Data source ID: [{}] URL: [{}]", dataSource.getId(), dataSource.getUrl());
						continue;
					}
				} else {
					logger.error("[ProductUtils] - No data source found by ID [{}]. Product ID [{}]", automobile.getDataSourceId(), automobile.getId());
					continue;
				}
				switch(dataSource.getDataSourceType()) {
					case LISTING:
						if(dataSource.getIndexOnly()) {
							// TODO:
							logger.trace("[ProductUtils] - You need to implement a generic builder for index only source [{}]", dataSource.getUrl());
							numNotTouched++;
							continue;
						}
						try {
							Class<?> clazz = Class.forName(dataSource.getBotClass());
							if(ListingAutomobileBot.class.isAssignableFrom(clazz)) {
								Object bot = clazz.getConstructor().newInstance();
								SpringUtils.autowire(bot);
								Method method = clazz.getDeclaredMethod("buildProduct", String.class);
								method.setAccessible(true);
								BuiltProduct refresh = (BuiltProduct)method.invoke(bot, automobile.getUrl());
								if(refresh.isMarkedForRemoval()) {
									automobileDao.delete(automobile);
									numRemoved++;
								} else {
									mergeProductFromRefresh(refresh.getProduct(), automobile, dataSource);
									refreshed.add((Automobile)refresh.getProduct());
								}
							} else {
								throw new IllegalAccessException("Bot class for data source is not of type ListingAutomobileBot");
							}
						} catch(Exception e) {
							logger.error("[ProductUtils] - Error running refresh for listing bot [{}]", ExceptionUtils.getRootCauseMessage(e));
						}
						break;
					case GENERIC:
						Document document = ConnectionAgent.INSTANCE.download(automobile.getUrl(), dataSource).getDocument();
						if(document == null) {
							automobileDao.delete(automobile);
							numRemoved++;
							continue;
						}
						Automobile refresh = automotiveGatherer.buildProduct(document);
						if(refresh != null) {
							refresh.setDataSourceId(dataSource.getId()); // needed for basic invalid check to handle deletion
							refresh.setDealerUrl(dataSource.getUrl());
						}
						if(basicInvalidator(refresh)) {
							automobileDao.delete(automobile);
							numRemoved++;
							continue;
						}
						mergeProductFromRefresh(refresh, automobile, dataSource);
						refreshed.add(refresh);
						break;
					case IMPORT_PROCESS:
						break;
					default:
						logger.error("[ProductUtils] - Invalid data source type found [{}]", dataSource.getDataSourceType());
						break;
				}
			}
			automobileDao.saveAll(refreshed);
			logger.debug(ConsoleColors.green("[ProductUtils] - Refreshed: [{}] Removed: [{}] Not touched: [{}] for VIN [{}]"),
					refreshed.size(), numRemoved, numNotTouched, vin);
		}
		if(product instanceof RealEstate) {
			logger.trace("[ProductUtils] - TODO: real estate refresh aggregates");
		}
		if(product instanceof Watercraft) {
			logger.trace("[ProductUtils] - TODO: watercraft refresh aggregates");
		}
		if(product instanceof Aircraft) {
			logger.trace("[ProductUtils] - TODO: aircraft refresh aggregates");
		}
		return true;
	}

	private static Date refreshThreshold() {
		return new Date(System.currentTimeMillis() - productRefreshThresholdMillis);
	}

	private void mergeProductFromRefresh(Product refresh, Product existing, DataSource dataSource) {
		switch(dataSource.getDataSourceType()) {
			case LISTING:
			case IMPORT_PROCESS:
				refresh.setSourceUrl(dataSource.getUrl());
				break;
			case GENERIC:
				if(refresh instanceof Automobile) {
					((Automobile)refresh).setDealerUrl(dataSource.getUrl());
				}
				if(refresh instanceof RealEstate) {
					((RealEstate)refresh).setRealtorUrl(dataSource.getUrl());
				}
				break;
		}
		refresh.setDataSourceId(dataSource.getId());
		if(basicInvalidator(refresh)) {
			return;
		}
		if(refresh instanceof Automobile) {
			automotiveGatherer.setAdditionalAttributes((Automobile)refresh);
		}
		if(existing.getMainImageUrl() != null && refresh.getMainImageUrl() == null) {
			refresh.setMainImageUrl(existing.getMainImageUrl());
		}
		Date now = new Date();
		refresh.setId(existing.getId());
		refresh.setCreationDate(existing.getCreationDate());
		refresh.setCreatedBy(existing.getCreatedBy());
		refresh.setVisitedBy(chloe);
		refresh.setVisitedDate(now);

		setModifiedIfPriceChange(refresh, existing, now);
	}

	private String getAggregationColumnValue(Product product) {
		String aggregationValue = null;
		for(Field f : product.getClass().getDeclaredFields()) {
			if(f.isAnnotationPresent(AggregationColumn.class)) {
				f.setAccessible(true);
				try {
					aggregationValue = (String)f.get(product);
				} catch(IllegalAccessException e) {
					logger.error("[ProductUtils] - Could not get aggregation column field!", e);
					return null;
				}
			}
		}
		if(aggregationValue == null) {
			logger.error("[ProductUtils] - Null aggregation column value for product [{}]", product);
		}
		return aggregationValue;
	}

	private boolean setModifiedIfPriceChange(Product product, Product existing, Date now) {
		if(Objects.equals(product.getPrice(), existing.getPrice())) {
			product.setModifiedDate(existing.getModifiedDate());
			product.setModifiedBy(existing.getModifiedBy());
			return false;
		} else {
			// 🚨 price change! 🚨
			product.setModifiedDate(now);
			product.setModifiedBy(chloe);
			return true;
		}
	}

	public boolean basicInvalidator(Product product) {
		if(product == null) {
			return true;
		}
		if(!UrlValidator.getInstance().isValid(product.getUrl())) {
			logger.debug("[ProductUtils] - Invalid URL [{}], not persisting. Product [{}]", product.getUrl(), product);
			return true;
		}
		if(product.getDataSourceId() == null) {
			logger.debug("[ProductUtils] - Missing data source ID for product, not persisting [{}]", product.getUrl());
			return true;
		}
		if(product instanceof RealEstate) {
			RealEstate realEstate = (RealEstate)product;
			if(StringUtils.isBlank(realEstate.getAddress())) {
				logger.debug("[ProductUtils] - Missing address for real estate, not persisting [{}]", product.getUrl());
				return true;
			}
			if(!realEstate.getAddress().matches("\\d+.*")) {
				logger.debug("[ProductUtils] - Address does not start with street number, not persisting [{}]", product.getUrl());
				return true;
			}
			if(StringUtils.isNumericSpace(realEstate.getAddress())) {
				logger.debug("[ProductUtils] - Address only numbers, not persisting [{}]", product.getUrl());
				return true;
			}
			if(StringUtils.isBlank(realEstate.getZip())) {
				logger.debug("[ProductUtils] - Missing zip for real estate, not persisting [{}]", product.getUrl());
				return true;
			}
			if(realEstate.getState() == null) {
				logger.debug("[ProductUtils] - Missing state for real estate, not persisting [{}]", product.getUrl());
				return true;
			}
			if(realEstate.getLatitude() == null || realEstate.getLongitude() == null) {
				logger.debug("[ProductUtils] - Missing lat or long for real estate, not persisting [{}]", product.getUrl());
				return true;
			}
			if(realEstate.getBeds() != null) {
				if(realEstate.getBeds() < 0 || realEstate.getBeds() > 30) {
					realEstate.setBeds(null);
				}
			}
			if(realEstate.getBaths() != null) {
				if(realEstate.getBaths() < 0 || realEstate.getBaths() > 30) {
					realEstate.setBaths(null);
				}
			}
		}
		if(product instanceof Automobile) {
			Automobile automobile = (Automobile)product;
			if(!AutoParsingOperations.vinRecognizer().test(automobile.getVin())) {
				logger.debug("[ProductUtils] - Invalid VIN for automobile, not persisting [{}]", product.getUrl());
				return true;
			}
			if(automobile.getMakeId() == null || automobile.getModelId() == null) {
				logger.debug("[ProductUtils] - Missing make or model for automobile, not persisting [{}]", product.getUrl());
				return true;
			}
		}
		if(product instanceof Watercraft) {
			Watercraft watercraft = (Watercraft)product;
			if(StringUtils.isBlank(watercraft.getManufacturer())) {
				logger.debug("[ProductUtils] - Missing manufacturer for watercraft, not persisting [{}]", product.getUrl());
				return true;
			}
			if(StringUtils.isBlank(watercraft.getModel())) {
				logger.debug("[ProductUtils] - Missing model for watercraft, not persisting [{}]", product.getUrl());
				return true;
			}
		}
		if(product instanceof Aircraft) {
			Aircraft aircraft = (Aircraft)product;
			if(StringUtils.isBlank(aircraft.getRegNumber())) {
				logger.debug("[ProductUtils] - Missing reg number for aircraft, not persisting [{}]", product.getUrl());
				return true;
			}
		}
		return false;
	}
}
