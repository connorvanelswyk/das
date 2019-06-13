
# LISTING BOT

insert into data_source (
	url,
	asset_type_id,
	data_source_type_id,
	temp_disabled,
	perm_disabled,
	proxy_mode,
	agent_mode,
	crawl_rate,
	max_queued_orders,
	days_between_runs,
	bot_class,
	created)
values
	(
		'https://YOUR-LISTING-SITE.com/',
		(select a.id
		 from asset_type a
		 where a.type = 'AIRCRAFT'),
		(select t.id
		 from data_source_type t
		 where t.type = 'LISTING'),
		1,
		1,
		'ROTATE_LOCATION',
		'ROTATE',
		4000,
		12,
		4,
		'com.plainviewrd.commons.bot.ASSET-PACKAGE.YOUR-BOT-CLASS',
		current_timestamp
	);





# IMPORT PROCESS

insert into data_source (
	url,
	asset_type_id,
	data_source_type_id,
	temp_disabled,
	perm_disabled,
	proxy_mode,
	agent_mode,
	crawl_rate,
	days_between_runs,
	bot_class,
	created)
values
	(
		'http://registry.faa.gov/',
		(select a.id
		 from asset_type a
		 where a.type = 'AIRCRAFT'),
		(select t.id
		 from data_source_type t
		 where t.type = 'IMPORT_PROCESS'),
		0,
		0,
		'ROTATE_LOCATION',
		'ROTATE',
		4000,
		99999,
		'com.plainviewrd.datasource.bot.aircraft.FaaImportProcess',
		current_timestamp
	);

