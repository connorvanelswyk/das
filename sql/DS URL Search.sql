set @search_url := 'https://www.phillipschevy.com/';

select *
from data_source
where id in (
	select id
	from (
		     select
			     id,
			     substring_index(
				     reverse(
					     substr(
						     reverse(no_protocol_ds_url), 1 + locate('/', reverse(no_protocol_ds_url))
					     )
				     ), 'www.', -1
			     ) stripped_ds_url,
			     substring_index(
				     reverse(
					     substr(
						     reverse(no_protocol_search_url), 1 + locate('/', reverse(no_protocol_search_url))
					     )
				     ), 'www.', -1
			     ) stripped_search_url
		     from (
			          select
				          id,
				          lower(
					          substring_index(
						          (case when right(url, 1) = '/'
							          then url
						           else concat(url, '/') end),
						          '://', -1
					          )
				          ) no_protocol_ds_url,
				          lower(
					          substring_index(
						          (case when right(@search_url, 1) = '/'
							          then @search_url
						           else concat(@search_url, '/') end),
						          '://', -1
					          )
				          ) no_protocol_search_url
			          from data_source # ************ CHANGE TABLE FOR SEARCH ************ #
		          ) protocol_stripped
	     ) prefix_stripped
	where binary stripped_ds_url = binary stripped_search_url
);
