select *
from INFORMATION_SCHEMA.TRIGGERS;



drop trigger if exists tr_automobile_price_update;

create trigger tr_automobile_price_update
	after update
	on automobile
	for each row
	begin
		if not (old.price <=> new.price)
		then
			insert into automobile_history (history_type, id, creation_date, created_by, modified_date, modified_by, visited_date, visited_by, url, dealer_url, dealer_name, source_url, stock_number, address, latitude, longitude, vin, make, model, year, mileage, price, exterior_color, interior_color, engine, transmission, drive_type, mpg_city, mpg_highway, doors, body_style, trim, listing_id)
			values
				(
					'PRICE_UPDATE',
					old.id,
					old.creation_date,
					old.created_by,
					current_timestamp(),
					old.modified_by,
					old.visited_date,
					old.visited_by,
					old.url,
					old.dealer_url,
					old.dealer_name,
					old.source_url,
					old.stock_number,
					old.address,
					old.latitude,
					old.longitude,
					old.vin,
					(case when old.make_id is null then null
					 else (select mk.make
					       from automobile_make mk
					       where mk.id = old.make_id) end),
					(case when old.make_id is null or old.model_id is null then null
					 else (select mo.model
					       from automobile_model mo
						       join automobile_make mk on mo.make_id = mk.id
					       where mo.id = old.model_id and mk.id = old.make_id) end),
					old.year,
					old.mileage,
					old.price,
					old.exterior_color,
					old.interior_color,
					old.engine,
					old.transmission,
					old.drive_type,
					old.mpg_city,
					old.mpg_highway,
					old.doors,
					old.body_style,
					(case when old.trim_id is null then null
					 else (select tr.trim
					       from automobile_model_trim tr
					       where tr.id = old.trim_id) end),
					old.listing_id);
		end if;
	end;


drop trigger if exists tr_automobile_delete;

create trigger tr_automobile_delete
	after delete
	on automobile
	for each row
	begin
		insert into automobile_history (history_type, id, creation_date, created_by, modified_date, modified_by, visited_date, visited_by, url, dealer_url, dealer_name, source_url, stock_number, address, latitude, longitude, vin, make, model, year, mileage, price, exterior_color, interior_color, engine, transmission, drive_type, mpg_city, mpg_highway, doors, body_style, trim, listing_id)
		values
			(
				'DELETE',
				old.id,
				old.creation_date,
				old.created_by,
				current_timestamp(),
				old.modified_by,
				old.visited_date,
				old.visited_by,
				old.url,
				old.dealer_url,
				old.dealer_name,
				old.source_url,
				old.stock_number,
				old.address,
				old.latitude,
				old.longitude,
				old.vin,
				(case when old.make_id is null then null
				 else (select mk.make
				       from automobile_make mk
				       where mk.id = old.make_id) end),
				(case when old.make_id is null or old.model_id is null then null
				 else (select mo.model
				       from automobile_model mo
					       join automobile_make mk on mo.make_id = mk.id
				       where mo.id = old.model_id and mk.id = old.make_id) end),
				old.year,
				old.mileage,
				old.price,
				old.exterior_color,
				old.interior_color,
				old.engine,
				old.transmission,
				old.drive_type,
				old.mpg_city,
				old.mpg_highway,
				old.doors,
				old.body_style,
				(case when old.trim_id is null then null
				 else (select tr.trim
				       from automobile_model_trim tr
				       where tr.id = old.trim_id) end),
				old.listing_id);
	end;

