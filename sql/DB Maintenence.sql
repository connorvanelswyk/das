
show global variables like 'innodb_data_file_path';
show global variables like 'max_heap_table_size';
show global variables like 'tmp_table_size';


select 1024 * 1024 * 1024 * 2;

# Sizes By Database
select
	DBName
	, CONCAT(LPAD(FORMAT(SDSize / POWER(1024, pw), 3), 17, ' '), ' ',
	         SUBSTR(' KMGTP', pw + 1, 1), 'B')                                                         "Data Size"
	, CONCAT(LPAD(
		         FORMAT(SXSize / POWER(1024, pw), 3), 17, ' '), ' ', SUBSTR(' KMGTP', pw + 1, 1), 'B') "Index Size"
	, CONCAT(LPAD(FORMAT(STSize / POWER(1024, pw), 3), 17, ' '), ' ',
	         SUBSTR(' KMGTP', pw + 1, 1), 'B')                                                         "Total Size"
from
	(select
		   IFNULL(DB, 'All Databases') DBName
		 , SUM(DSize)                  SDSize
		 , SUM(XSize)                  SXSize
		 , SUM(TSize)                  STSize
	 from (select
		         table_schema               DB
		       , data_length                DSize
		       , index_length               XSize
		       , data_length + index_length TSize
	       from information_schema.tables
		      # 	       where table_schema not in
		      # 	             ('mysql', 'information_schema', 'performance_schema')
	      ) AAA
	 group by DB with rollup) AA, (select 3 pw) BB
order by (SDSize + SXSize);





show variables like 'general_log%';
show variables like 'slow%';
show variables like 'long_query_time';

show full processlist;

show databases;


select *
from mysql.slow_log;


select *
from mysql.user;

select *
from mysql.general_log
order by event_time desc;

select format(count(1), 0) log_size
from mysql.general_log;

select *
from mysql.general_log
where command_type = 'Query'
      and user_host not like 'rdsadmin%'
      and user_host like '%webapp%'
order by event_time desc;


select *
from mysql.general_log
where command_type = 'Connect'
# 	and argument like '%denied%'
order by event_time desc;



select *
from mysql.general_log
where user_host like '%chadillac%'
order by event_time desc;


select *
from mysql.general_log
where user_host not like 'rdsadmin%' # admin process
      and user_host not like '%68.202.197.72%' # us
      and user_host not like '%172.31.3.152%' # webapp

      and user_host not like '%45.32.214.213%'
      and user_host not like '%45.32.223.225%'
      and user_host not like '%104.156.254.60%'
      and user_host not like '%45.32.216.228%'
      and user_host not like '%108.61.252.243%'
      and user_host not like '%45.32.210.171%'
      and user_host not like '%45.76.63.144%'
      and user_host not like '%45.76.229.232%'
      and user_host not like '%45.63.79.92%'
      and user_host not like '%45.76.16.10%'
      and user_host not like '%45.76.27.177%'
      and user_host not like '%45.63.65.184%'
      and user_host not like '%45.76.229.186%'
      and user_host not like '%45.77.192.89%'
      and user_host not like '%207.246.67.53%'
      and user_host not like '%45.32.160.181%'
      and user_host not like '%207.246.72.215%'
      and user_host not like '%45.77.72.205%'
      and user_host not like '%45.32.173.108%'
      and user_host not like '%207.246.70.252%'
      and user_host not like '%45.77.107.25%'
      and user_host not like '%45.77.101.238%'
      and user_host not like '%207.246.88.162%'
      and user_host not like '%207.246.84.68%'
      and user_host not like '%45.77.222.139%'
      and user_host not like '%104.238.135.130%'
      and user_host not like '%104.156.254.27%'
      and user_host not like '%144.202.115.93%'
      and user_host not like '%144.202.115.243%'
      and user_host not like '%144.202.115.155%'
      and user_host not like '%144.202.114.237%'
      and user_host not like '%144.202.116.68%'
      and user_host not like '%144.202.84.97%'
      and user_host not like '%144.202.84.100%'
      and user_host not like '%144.202.84.186%'
      and user_host not like '%144.202.84.216%'
      and user_host not like '%144.202.84.241%'
      and user_host not like '%144.202.100.42%'
      and user_host not like '%144.202.100.86%'
      and user_host not like '%144.202.100.125%'
      and user_host not like '%144.202.99.226%'
      and user_host not like '%144.202.99.200%'
      and user_host not like '%207.148.15.16%'
      and user_host not like '%45.76.16.13%'
      and user_host not like '%207.148.12.51%'
      and user_host not like '%207.148.12.165%'
      and user_host not like '%144.202.19.239%'
      and user_host not like '%144.202.20.15%'
      and user_host not like '%144.202.20.28%'
order by event_time desc;

