-- 商品资质与批次检测报告附件。
--
-- 复用 t_file_relation 而不是另建表：绑定幂等、软删撤销、三元组唯一索引、读放行注册表都已存在，
-- 新建一张表要把这些重写一遍。代价只是 biz_type 白名单多两个值。
--
-- 两个新类型的对象：
--   * PRODUCT_QUALIFICATION：biz_id = xsy_product_spu.id（营业执照、生产许可等商品/供应商资质）
--   * LOT_INSPECTION：biz_id = xsy_trace_lot.id（该批次的检测报告）
--
-- 出入库不挂附件，只在扫码页展示记录；这里不新增单据类附件。

ALTER TABLE t_file_relation DROP CONSTRAINT ck_t_file_relation_biz_type;
ALTER TABLE t_file_relation ADD CONSTRAINT ck_t_file_relation_biz_type CHECK (biz_type IN
    ('ENTERPRISE', 'NOTICE', 'HELP_DOC', 'FEEDBACK', 'PRODUCT',
     'PRODUCT_QUALIFICATION', 'LOT_INSPECTION'));

COMMENT ON COLUMN t_file_relation.biz_type IS
    '业务对象类型白名单：ENTERPRISE 企业档案 / NOTICE 公告 / HELP_DOC 帮助文档 / FEEDBACK 反馈 / PRODUCT 商品图 / PRODUCT_QUALIFICATION 商品资质 / LOT_INSPECTION 批次检测报告';

-- 资质与报告的查看沿用既有权限：能看商品就能看它的资质，能看包装码就能看批次报告。
-- 只有「管理」单独授一枚权限，避免给采购/质检岗顺带打开商品或库存的写入口。
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component,
                   perms_type, api_perms, web_perms, context_menu_id, visible_flag, create_user_id)
VALUES (2123, '商品资质附件管理', 3, 402, 2123, NULL, NULL, 1,
        'scm:trace:file:manage', 'scm:trace:file:manage', 402, TRUE, 1),
       (2124, '批次检测报告管理', 3, 2110, 2124, NULL, NULL, 1,
        'scm:trace:file:manage', 'scm:trace:file:manage', 2110, TRUE, 1)
ON CONFLICT(menu_id) DO NOTHING;

-- 2123 随商品档案授给商品维护岗；2124 随包装码授给仓库岗。
INSERT INTO t_role_menu(role_id, menu_id)
SELECT r.role_id, m.menu_id FROM t_role r CROSS JOIN t_menu m
WHERE m.menu_id = 2123
  AND (r.role_id = 1 OR r.role_code IN ('SCM_PRODUCT_MANAGER', 'SCM_PURCHASER'))
  AND NOT EXISTS (SELECT 1 FROM t_role_menu p WHERE p.role_id = r.role_id AND p.menu_id = m.menu_id);

INSERT INTO t_role_menu(role_id, menu_id)
SELECT r.role_id, m.menu_id FROM t_role r CROSS JOIN t_menu m
WHERE m.menu_id = 2124
  AND (r.role_id = 1 OR r.role_code IN ('SCM_STOREKEEPER', 'SCM_STOREKEEPER_LEAD'))
  AND NOT EXISTS (SELECT 1 FROM t_role_menu p WHERE p.role_id = r.role_id AND p.menu_id = m.menu_id);
