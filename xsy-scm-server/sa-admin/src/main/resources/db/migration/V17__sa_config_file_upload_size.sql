-- F0 HD-2: data only. Preserve every other level3 protection setting and V1-V16.
UPDATE t_config
SET config_value = (config_value::jsonb || jsonb_build_object('maxUploadFileSizeMb', 20))::text,
    update_time  = CURRENT_TIMESTAMP
WHERE config_key = 'level3_protect_config';
