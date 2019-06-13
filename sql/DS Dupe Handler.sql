#########################################




# IF THE DUPE-EE IS PERM_DISABLED, DELETE THE AUTOMOBILES OR ELSE THEY WILL NEVER UPDATE




#########################################


# check the autos, should be empty after
select
	a.id
	, a.dealer_url
	, d.url
from automobile a
	join data_source d on a.dealer_url = d.url
where d.status_reason = 'DUPLICATE' and a.source_url is null;



drop table if exists ds_dupes;
create temporary table ds_dupes as (
	select
		distinct
		url
		, substring_index(substring_index(details, 'of ', -1), ']', 1) dupe
	from data_source
	where status_reason = 'DUPLICATE' and substring_index(substring_index(details, 'of ', -1), ']', 1) in (
		select url
		from data_source));



select *
from ds_dupes;

# update the autos
update automobile a
	join ds_dupes d on a.dealer_url = d.url
set dealer_url = d.dupe
where source_url is null;



select d.*
from data_source d
	join ds_dupes dupes on dupes.dupe = d.url;

# migrate the stats
update data_source new
	join ds_dupes dupe on dupe.dupe = new.url
	join data_source old on dupe.url = old.url
set new.total_runs          = new.total_runs + old.total_runs, new.total_time = new.total_time + old.total_time,
	new.total_product_count = new.total_product_count + old.total_product_count,
	new.total_visited_urls  = new.total_visited_urls + old.total_visited_urls,
	new.total_analyzed_urls = new.total_analyzed_urls + old.total_analyzed_urls,
	new.total_download_mb   = new.total_download_mb + old.total_download_mb
where new.bot_class is null; # < safety?


# delete the old data sources
delete d
from data_source d
	join ds_dupes dupes on dupes.url = d.url
where d.status_reason = 'DUPLICATE';

# should be empty after
select d.*
from data_source d
	join ds_dupes dupes on dupes.url = d.url;



drop table ds_dupes;

