-- 职务不参与鉴权，与角色重复；菜单已在 V238 下线，这里清掉残余字段与表。
ALTER TABLE t_employee
    DROP COLUMN IF EXISTS position_id;

DROP TABLE IF EXISTS t_position;
