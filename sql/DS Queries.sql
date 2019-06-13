

# HOPE THIS NEVER RETURNS RESULTS
select *
from data_source
where details like '%Explicit user agent%';


# TOTAL STATUS TO REASON COUNT BREAKDOWN
select
	status
	, status_reason
	, format(count(1), 0)                                                  count
	, format(sum(total_product_count), 0)                                  total_product_yield
	, format(sum(last_product_count), 0)                                   last_product_yield
	, date(max(case when perm_disabled = 0 then last_run
	           else null end))                                             latest_run_enabled
	, date(min(case when perm_disabled = 0 then last_run
	           else null end))                                             oldest_run_enabled
	, date(max(case when perm_disabled = 1 then last_run
	           else null end))                                             latest_run_disabled
	, date(min(case when perm_disabled = 1 then last_run
	           else null end))                                             oldest_run_disabled
	, case when sum(perm_disabled) = 0 then 'none'
	  else concat(format(sum(perm_disabled) / count(1) * 100, 2), '%') end percent_disabled
from data_source
where bot_class is null
group by status_reason, status
order by count(1) desc;





# LISTING SOURCE FRESH VIN RATE
select
	concat(format(100 - (stale / total * 100), 2), '%') fresh_vin_rate
	, stale
	, total
from (select count(distinct a.vin) stale
      from automobile a
	      join automobile b on a.vin = b.vin and not (a.source_url <=> b.source_url)
      where a.source_url = 'https://www.carstory.com/') a, (select count(distinct a.vin) total
                                                          from automobile a
                                                          where a.source_url = 'https://www.carstory.com/') b;




# SUCCESS TO FAILURE RATE
select
	  concat(round(failed / total * 100, 2), '%')  failure_rate
	, concat(round(success / total * 100, 2), '%') success_rate
	, format(total, 0)                             total
from (
	     select
		       count(1)        total
		     , sum(case when status = 'FAILURE'
		     then 1
		           else 0 end) failed
		     , sum(case when status = 'SUCCESS'
		     then 1
		           else 0 end) success
	     from data_source
	     where perm_disabled = 0 and not (status_reason <=> 'SHUTDOWN')) a;



# LAST RUN GROUPED BY DATE
select
	  date(last_run) run
	, count(1)       cnt
from data_source
where perm_disabled = 0
	and bot_class is null
group by run;



# ALL THE STATS <3
select
	  format(sum(total_runs), 0)                              as TotalRuns
	, format(sum(total_analyzed_urls), 0)                     as TotalAnalyzed
	, format(sum(total_visited_urls), 0)                      as TotalVisited
	, format(sum(total_product_count), 0)                     as TotalProductYield
	, format(sum(total_download_mb / 1000000), 2)             as TotalDownloadTB
	, (
		  select concat(floor(sum(total_time) / (86400) / 365), ' years ',
		                floor(sum(total_time) / (86400)) - floor(sum(total_time) / (86400) / 365) * 365, ' days ',
		                time_format(sec_to_time(s % (3600 * 24)), '%Hh %im %ss')) f
		  from (
			       select (
				              select sum(total_time)
				              from data_source) s) t)         as TotalTime
	, concat(format(avg(crawl_rate) / 1000, 2), 's')          as AverageCrawlRate
	, time_format(sec_to_time(avg(last_time)), '%Hh %im %ss') as AvgRunTime
	, avg(last_product_count)                                 as AvgProductYield
from data_source;


# TotalRuns	TotalAnalyzed	TotalProductCount	TotalDownloadedTB	TotalTime	        AverageCrawlRate	AvgRunTime	    AvgProductCount
# 214,062	2,025,920,589	44,676,784	        18.62	            12 years 164 days   07h 02m 51s	1.66s	00h 05m 30s	    198.0566




# HISTORY COUNTS
select
	history_type
	, format(count(1), 0) cnt
from automobile_history
group by history_type;




