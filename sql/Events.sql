show variables like '%event_scheduler%';
show variables like '%time_zone%';
show events;
select *
from information_schema.EVENTS;

# # drop event dev_automobile_sync;
# create event dev_automobile_sync
# 	on schedule
# 		every 1 week
# 		starts '2018-01-29 08:33:33' # every tuesday at 3:33am utc-5
# do
# 	begin
# 		drop table dev.automobile;
# 		create table dev.automobile
# 			like prod.automobile;
# 		insert dev.automobile
# 			select *
# 			from prod.automobile;
# 	end;



# drop event dev_sync;

create event dev_sync
	on schedule
		every 1 day
		starts (timestamp(current_date) + interval 1 day + interval 8 hour + interval 33 minute + interval 33 second)
do
	begin
		# every monday #and thursday
		if weekday(current_date) = 0 # or weekday(current_date) = 3
		then
			set session transaction isolation level read uncommitted;
			drop table dev.automobile;
			create table dev.automobile
				like prod.automobile;
			insert dev.automobile
				select *
				from prod.automobile;

			delete from dev.listing_data_source_urls;
			alter table dev.listing_data_source_urls
				auto_increment = 1;


			drop table dev.data_source;
			create table dev.data_source
				like prod.data_source;
			insert dev.data_source
				select *
				from prod.data_source;
			update dev.data_source
			set running = 0, staged = 0, temp_disabled = 1;

			drop table dev.page_meta;
			create table dev.page_meta
				like prod.page_meta;
			insert dev.page_meta
				select *
				from prod.page_meta;

			set foreign_key_checks = 0;
			drop table if exists dev.automobile_model_ext_color;
			drop table if exists dev.automobile_model_int_color;
			drop table if exists dev.automobile_model_price_range;
			drop table if exists dev.automobile_model_year_range;
			drop table if exists dev.automobile_model_trim;
			drop table if exists dev.automobile_model;
			drop table if exists dev.automobile_make;
			set foreign_key_checks = 1;

			create table dev.automobile_make
				like prod.automobile_make;
			insert dev.automobile_make
				select *
				from prod.automobile_make;

			create table dev.automobile_model
				like prod.automobile_model;
			insert dev.automobile_model
				select *
				from prod.automobile_model;

			create table dev.automobile_model_trim
				like prod.automobile_model_trim;
			insert dev.automobile_model_trim
				select *
				from prod.automobile_model_trim;

			create table dev.automobile_model_year_range
				like prod.automobile_model_year_range;
			insert dev.automobile_model_year_range
				select *
				from prod.automobile_model_year_range;

			create table dev.automobile_model_price_range
				like prod.automobile_model_price_range;
			insert dev.automobile_model_price_range
				select *
				from prod.automobile_model_price_range;

			create table dev.automobile_model_int_color
				like prod.automobile_model_int_color;
			insert dev.automobile_model_int_color
				select *
				from prod.automobile_model_int_color;

			create table dev.automobile_model_ext_color
				like prod.automobile_model_ext_color;
			insert dev.automobile_model_ext_color
				select *
				from prod.automobile_model_ext_color;

			set session transaction isolation level repeatable read;
		end if;
	end;

