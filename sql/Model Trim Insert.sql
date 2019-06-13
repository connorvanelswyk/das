create procedure model_insert(
	  make_val  varchar(255)
	, model_val varchar(255)
	, min_year  smallint
	, max_year  smallint
	, min_price mediumint
	, max_price mediumint
	, body      tinyint
	, fuel      tinyint
	, trans     tinyint
	, drive     tinyint
)
	begin
		set @make_id = (select id
		                from automobile_make
		                where make = make_val);
		insert into automobile_model (make_id, model, allow_direct_trim_match, allow_trim_concat_match)
		values
			(@make_id, @model, 0, 0);
		set @model_id = (select id
		                 from automobile_model
		                 where model = model_val);
		insert into automobile_model_year_range (model_id, year_min, year_max)
		values
			(@model_id, min_year, max_year);
		insert into automobile_model_price_range (model_id, price_min, price_max)
		values
			(@model_id, min_price, max_price);
		insert into automobile_model_body_xref (model_id, body_id)
		values
			(@model_id, body);
		insert into automobile_model_fuel_xref (model_id, fuel_id)
		values
			(@model_id, fuel);
		insert into automobile_model_transmission_xref (model_id, transmission_id)
		values
			(@model_id, trans);
		insert into automobile_model_drivetrain_xref (model_id, drivetrain_id)
		values
			(@model_id, drive);
	end;

create procedure trim_insert(
	  make_val  varchar(255)
	, model_val varchar(255)
	, trim_val  varchar(255)
)
	begin
		set @model_id = (select mo.id
		                 from automobile_model mo
			                 join automobile_make mk on mk.id = mo.make_id
		                 where mo.model = model_val and mk.make = make_val);
		insert into automobile_model_trim (model_id, trim)
		values
			(@model_id, trim_val);
	end;


call model_insert('Ford', 'C-Max', 2013, 2018, 5000, 40000, 5, 2, 0, 0);
call trim_insert('Ford', 'C-Max', 'SE');
call trim_insert('Ford', 'C-Max', 'SEL');


