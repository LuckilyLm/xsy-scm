-- Keep API permission and SmartAdmin web directive permission aligned.
UPDATE t_menu SET web_perms = api_perms, visible_flag = TRUE WHERE menu_id IN (47501, 47502);
UPDATE t_menu SET parent_id = 602 WHERE menu_id = 47502;
