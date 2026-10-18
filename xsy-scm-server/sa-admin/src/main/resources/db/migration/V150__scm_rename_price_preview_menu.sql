-- 菜单名与页面卡片标题统一为「价格试算」。
--
-- 菜单 506 在库里叫「取价试算」，页面标题却是「价格试算」，同一个功能两个叫法。
-- 菜单名只存在 t_menu.menu_name，前端文案契约只扫源码、扫不到，改名只能靠迁移；
-- 路径 /pricing/price-preview、组件与权限码都保持不动。
UPDATE xsy_v2.t_menu
SET menu_name = '价格试算', update_time = now()
WHERE menu_id = 506;
