
# GENERIC DATA SOURCES
select *
from data_source
where bot_class is null;

# LISTING DATA SOURCES VIEW
select
	id
	, url
	, running                              rn
	, staged                               sg
	, temp_disabled                        td
	, perm_disabled                        pd
	, stealth_mode                         sm
	, crawl_rate                           cr
	, index_only                           ixo
	, index_del_size                       ids
	, max_queued_orders                    mqo
	, days_between_runs                    dbr
	, date_format(last_run, '%m-%d %h:%m') lr
	, status
	, status_reason                        reason
	, details
from data_source
where bot_class is not null
order by perm_disabled, index_only desc, last_run is not null, last_run desc;


# LISTING DATA SOURCES MODIFY
select
	id
	, url
	, running
	, staged
	, temp_disabled
	, perm_disabled
	, stealth_mode
	, crawl_rate
	, index_only
	, index_del_size
	, max_queued_orders
	, days_between_runs
	, last_run
	, status
	, status_reason
	, details
from data_source
where bot_class is not null
order by perm_disabled, index_only desc, last_run is not null, last_run desc;




# CLUSTER NODES
select *
from master_node;

select *
from worker_node;

select *
from last_node_addresses;




# LISTING DATA SOURCE URLS

select
	source
	, case when base_total = 0 then 'done'
	  else format(base_total, 0) end  base_total
	, case when base_total = 0 then 'done'
	  else format(base_ready, 0) end  base_ready
	, case when base_total = 0 then 'done'
	  else format(base_ran, 0) end    base_ran
	, case when base_total = 0 then 'done'
	  else concat(format(base_ran / base_total * 100, 2), '% ',
	              case when base_total > 0 and base_ran = base_total then ' (finishing)'
	              else '' end) end    base_progress
	, '|'                             x
	, case when build_ready = -1 then 'n/a'
	  else format(build_total, 0) end build_total
	, case when build_ready = -1 then 'n/a'
	  else format(build_ready, 0) end build_ready
	, case when build_ready = -1 then 'n/a'
	  else format(build_ran, 0) end   build_ran
	, case when build_ready = -1 then 'n/a'
	  else concat(format(build_ran / build_total * 100, 2), '% ',
	              case when build_total > 0 and build_ran = build_total then ' (finishing)'
	              else '' end) end    build_progress
from (
	     select
		       substring_index(
			       substring_index(
				       substring_index(d.url, '/', 3)
				       , '://', -1)
			       , '/', 1)
			                            source
		     , sum(base)                base_total
		     , sum(case when base then 0
		           else 1 end)          build_total
		     , sum(case when not ran and base then 1
		           else 0 end)          base_ready
		     , sum(case when ran and base then 1
		           else 0 end)          base_ran
		     , case when index_only then -1
		       else sum(case when not ran and not base then 1
		                else 0 end) end build_ready
		     , case when index_only then -1
		       else sum(case when ran and not base then 1
		                else 0 end) end build_ran
	     from listing_data_source_urls
		     join data_source d on d.id = data_source_id
	     group by data_source_id
     ) ran_stats;







# RUNNING, QUEUED, STAGED, READY, FUTURE

select
	  'generic' as                     type
	, format(run_actual, 0)            running
	, format(run_flag - run_actual, 0) queued
	, format(staged, 0)                staged
	, format(ready, 0)                 ready
	, future                           scheduled
from (select
	        sum(case when bot_class is null then running
	            else 0 end)                                                                          run_flag
	      , sum(case when running = 1 and bot_class is null
	                      and exists(select 1
	                                 from worker_node
	                                 where work_description like concat('%', url, '%')) then 1
	            else 0 end)                                                                          run_actual
	      , sum(case when bot_class is null then staged
	            else 0 end)                                                                          staged
	      , (select count(1)
	         from data_source
	         where temp_disabled = 0 and perm_disabled = 0 and running = 0 and staged = 0 and bot_class is null
	               and (last_run is null
	                    or last_run < date_sub(now(), interval days_between_runs day)))              ready
	      , (select group_concat(
		                concat(last_run_date, ': ', rpad(format(future_ready_count, 0), 7, ' '))
		                separator '  |  ') next_ready
	         from (select
		                 case when date_format(now(), '%m-%d') =
		                           date_format(date_add(last_run, interval days_between_runs day), '%m-%d') then 'today'
		                 else date_format(date_add(last_run, interval days_between_runs day), '%m-%d') end
			                      last_run_date
		               , count(1) future_ready_count
	               from data_source
	               where temp_disabled = 0 and perm_disabled = 0 and running = 0 and staged = 0
	                     and bot_class is null and last_run > date_sub(now(), interval days_between_runs day)
	               group by last_run_date
	               order by if(last_run_date rlike '^[a-z]', 1, 2), last_run_date) future_breakdown) future
      from data_source
     ) stats
union
select
	  'listing' as                     type
	, format(run_actual, 0)            running
	, format(run_flag - run_actual, 0) queued
	, format(staged, 0)                staged
	, format(ready, 0)                 ready
	, future                           scheduled
from (select
	        sum(case when bot_class is not null then running
	            else 0 end)                                                                          run_flag
	      , sum(case when running = 1 and bot_class is not null
	                      and exists(select 1
	                                 from worker_node
	                                 where work_description like concat('%', url, '%')) then 1
	            else 0 end)                                                                          run_actual
	      , sum(case when bot_class is not null then staged
	            else 0 end)                                                                          staged
	      , (select count(1)
	         from data_source
	         where temp_disabled = 0 and perm_disabled = 0 and running = 0 and staged = 0 and bot_class is not null
	               and (last_run is null
	                    or last_run < date_sub(now(), interval days_between_runs day)))              ready
	      , (select group_concat(
		                concat(last_run_date, ': ', rpad(format(future_ready_count, 0), 7, ' '))
		                separator '  |  ') next_ready
	         from (select
		                 case when date_format(now(), '%m-%d') =
		                           date_format(date_add(last_run, interval days_between_runs day), '%m-%d') then 'today'
		                 else date_format(date_add(last_run, interval days_between_runs day), '%m-%d') end
			                      last_run_date
		               , count(1) future_ready_count
	               from data_source
	               where temp_disabled = 0 and perm_disabled = 0 and running = 0 and staged = 0
	                     and bot_class is not null and last_run > date_sub(now(), interval days_between_runs day)
	               group by last_run_date
	               order by if(last_run_date rlike '^[a-z]', 1, 2), last_run_date) future_breakdown) future
      from data_source
     ) stats;






select sec_to_time(avg(last_time))
from data_source
where last_run between '2018-03-09 01:00:00' and '2018-03-09 02:00:00'
	and bot_class is null
and perm_disabled = 0;


select sum(last_product_count), sum(total_runs), avg(last_product_count)
from data_source
where created > '2018-03-11'
and last_run is not null;


# update data_source d
# set
# 	  d.last_time           = coalesce(d.last_time          ,0) + coalesce(?, 0)
# 	, d.total_time          = coalesce(d.total_time         ,0) + coalesce(?, 0)
# 	, d.last_product_count  = coalesce(d.last_product_count ,0) + coalesce(?, 0)
# 	, d.total_product_count = coalesce(d.total_product_count,0) + coalesce(?, 0)
# 	, d.last_visited_urls   = coalesce(d.last_visited_urls  ,0) + coalesce(?, 0)
# 	, d.total_visited_urls  = coalesce(d.total_visited_urls ,0) + coalesce(?, 0)
# 	, d.last_analyzed_urls  = coalesce(d.last_analyzed_urls ,0) + coalesce(?, 0)
# 	, d.total_analyzed_urls = coalesce(d.total_analyzed_urls,0) + coalesce(?, 0)
# 	, d.last_download_mb    = coalesce(d.last_download_mb   ,0) + coalesce(?, 0)
# 	, d.total_download_mb   = coalesce(d.total_download_mb  ,0) + coalesce(?, 0)
# where d.id = ?;




# TOTAL PRODUCTS IN THE LAST X DAYS
select
	date(last_run) LastRun

	 , format(sum(last_product_count), 0) LastProductCount
	, format(count(1), 0)                NumDS
from data_source
where  status = 'SUCCESS'
group by LastRun
order by LastRun;


# VISITED CARS SINCE X
select format(count(1), 0) VisitedCars
from automobile
where visited_date > timestamp(curdate() - interval 1 day);



# TOTAL CARS BY SOURCES
select
	  case when source_url is null then 'Dealerships'
	  else source_url end source
	, format(total, 0)    count
from (
	     select distinct
		     source_url
		     , count(1) total
	     from automobile
	     group by source_url
     ) totals
order by total desc;


select price, url, mk.make, mo.model, a.visited_date
from automobile a
	join automobile_make mk on mk.id = a.make_id
	join automobile_model mo on mo.make_id = mk.id
where mk.make = 'Ford'
      and mo.model = 'F-150'
order by price desc;


# LAST VISITED GROUP BY
select
	  date(visited_date)  LastVisitedDay
	, format(count(1), 0) NumCars
	, group_concat(distinct coalesce(source_url, dealer_url) separator ', ')
from automobile
group by LastVisitedDay
order by LastVisitedDay;


select distinct
	dealer_url
	, status
	, status_reason
	, failed_attempts
	, last_run
	, running
	, date(min(visited_date))
	, date(max(visited_date))
	, count(1)
from automobile
	join data_source on data_source.url = automobile.dealer_url
where
	source_url is null
	and visited_date < timestamp(current_date - interval 2 week)
group by dealer_url
order by status_reason desc, count(1) desc;

