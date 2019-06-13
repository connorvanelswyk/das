############ FIND THE DUPES ############

create temporary table if not exists ds_dupes as (
	select
		lower(
			substring_index(
				(case when right(url, 1) = '/'
					then url
				 else concat(url, '/') end),
				'://', -1
			)
		)        no_protocol_ds_url,
		min(url) http,
		max(url) https,
		count(1) count
	from data_source
	group by no_protocol_ds_url
	having count = 2
);

create index ix_dupe_http
	on ds_dupes (http);
create index ix_dupe_https
	on ds_dupes (https);



############ MIGRATE ZE DATA SOURCES ############

update
# select * from
		data_source new
		inner join ds_dupes dupe on dupe.https = new.url
		inner join data_source old on dupe.http = old.url
set
	new.total_runs          = new.total_runs + old.total_runs,
	new.total_time          = new.total_time + old.total_time,
	new.total_product_count = new.total_product_count + old.total_product_count,
	new.total_visited_urls  = new.total_visited_urls + old.total_visited_urls,
	new.total_analyzed_urls = new.total_analyzed_urls + old.total_analyzed_urls,
	new.total_download_mb   = new.total_download_mb + old.total_download_mb
where new.bot_class is null; # < safety?



############ DELETE THE OLD DS ############

delete from data_source where url in (select http from ds_dupes);


############ UPDATE ZE AUTOS ############

update
# select count(1) from
		automobile a
		inner join ds_dupes dupes on a.dealer_url = dupes.http
set a.dealer_url = dupes.https
where a.source_url is null;



# now do all the same for the slightly different www discrepancy cases

create temporary table if not exists ds_www_dupes as (
	select
		(case when no_www like '%www%'
			then www
		 else no_www end) as no_www,
		(case when www like '%www%'
			then www
		 else no_www end) as www
	from (
		     select
			     lower(
				     substring_index(
					     (case when right(url, 1) = '/'
						     then url
					      else concat(url, '/') end),
					     (case when url like '%://www.%'
						     then '://www.'
					      else '://' end), -1
				     )
			     )        no_protocol_ds_url,
			     min(url) no_www,
			     max(url) www,
			     count(1) count
		     from data_source
		     group by no_protocol_ds_url
		     having count = 2)
	     search
);

create index ix_dupe_www
	on ds_www_dupes (www);
create index ix_dupe_no_www
	on ds_www_dupes (no_www);


update
# select * from
		data_source new
		inner join ds_www_dupes dupe on dupe.no_www = new.url
		inner join data_source old on dupe.www = old.url
set
	new.total_runs          = new.total_runs + old.total_runs,
	new.total_time          = new.total_time + old.total_time,
	new.total_product_count = new.total_product_count + old.total_product_count,
	new.total_visited_urls  = new.total_visited_urls + old.total_visited_urls,
	new.total_analyzed_urls = new.total_analyzed_urls + old.total_analyzed_urls,
	new.total_download_mb   = new.total_download_mb + old.total_download_mb
where new.bot_class is null; # < safety?


delete from data_source where url in (select www from ds_www_dupes);

update
# select count(1) from
		automobile a
		inner join ds_www_dupes dupes on a.dealer_url = dupes.www
set a.dealer_url = dupes.no_www
where a.source_url is null;




############ UPDATE AUTOMOBILES THAT HAVE A DEALER URL NOT IN DATA_SOURCE ############

# this has to be zero, or else they will sit there forever


create temporary table if not exists orphaned_dealer_urls as (
	select
		distinct a.dealer_url
	from automobile a
	where a.source_url is null and a.dealer_url not in (select d.url from data_source d)
);


select * from orphaned_dealer_urls;

# if not many and old datas:
# delete a
# from automobile a
# 	inner join orphaned_dealer_urls o on o.dealer_url = a.dealer_url;


create temporary table if not exists orphaned_dealers_to_actual_ds as (
	select
		o.dealer_url orphaned_dealer_url,
		d.url        actual_ds_url
	from orphaned_dealer_urls o
		inner join data_source d on substring_index(
			                            (case when right(d.url, 1) = '/'
				                            then d.url
			                             else concat(d.url, '/') end),
			                            '://', -1
		                            ) = substring_index(
			                            (case when right(o.dealer_url, 1) = '/'
				                            then o.dealer_url
			                             else concat(o.dealer_url, '/') end),
			                            '://', -1
		                            )
);


update automobile a
	inner join orphaned_dealers_to_actual_ds o on o.orphaned_dealer_url = a.dealer_url
set a.dealer_url = o.actual_ds_url;




############ AND FINALLY ############

drop table ds_dupes;
drop table ds_www_dupes;
drop table orphaned_dealer_urls;
drop table orphaned_dealers_to_actual_ds;

