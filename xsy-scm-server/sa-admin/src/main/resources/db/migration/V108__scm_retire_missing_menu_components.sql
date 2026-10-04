-- Remove the SmartAdmin demo page and the unused visibility-update action.
-- Customer SKU visibility is implemented by the V2 reverse list and customer create/detail workflow.
UPDATE t_menu
SET visible_flag = FALSE,
    disabled_flag = TRUE,
    deleted_flag = TRUE
WHERE (menu_id = 85 AND component = '/support/demonstration/index.vue')
   OR (menu_id = 487 AND api_perms = 'scm:customer:visibility:update');

DELETE FROM t_role_menu rm
USING t_menu m
WHERE rm.menu_id = m.menu_id
  AND m.menu_id IN (85, 487)
  AND m.deleted_flag = TRUE;
