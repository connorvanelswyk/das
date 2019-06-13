
# create table most_hist_ids as
# 	select id
# 	from automobile_history
# 	where id > 4000000
# 	group by id
# 	having count(1) > 7;

select *
from (
	     select
		       h.id           auto_id
		     , h.history_id   hist_id
		     , substring_index(
			       substring_index(
				       substring_index(
					       substring_index(h.url, '/', 3)
					       , '://', -1)
				       , '/', 1)
			       , '?', 1)  car_domain
		     , h.history_type type
		     , h.price        price
		     , h.visited_date changed_on
	     from automobile_history h
	     # 	 join most_hist_ids m on m.id = h.id
	     where h.history_type = 'PRICE_UPDATE'
	           and h.id > 60000000
	     union
	     select
		       a.id            auto_id
		     , null            hist_id
		     , substring_index(
			       substring_index(
				       substring_index(
					       substring_index(a.url, '/', 3)
					       , '://', -1)
				       , '/', 1)
			       , '?', 1)   car_domain
		     , 'CURRENT'       type
		     , a.price         price
		     , a.modified_date changed_on
	     from automobile a
		     join automobile_history h on a.id = h.id
	     # 	 join most_hist_ids m on m.id = h.id
	     where a.id > 60000000
     ) history
order by auto_id desc, changed_on desc;

# drop table most_hist_ids;



select
	url
	, price
from automobile
where id in (
	select id
	from automobile_history
	group by id
	having count(1) > 5
);
















































