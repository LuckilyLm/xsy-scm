-- 收起两个没有页面的菜单：菜单行在库里，但 component 为空，前端也没有对应路由与视图，
-- 点开只会得到一个空白页签（供应链工作台 / 业务待办）。
--
-- 为什么不是补页面：
--   * 业务待办已经以首页卡片（/scm/dashboard/todo）的形式存在，重复建页要先定口径；
--   * 工作台要看的已确认订单、采购、库存指标在「报表中心 → 经营概览」与「数据大屏」里。
-- 先隐藏入口，等负责人确认这两个页签各自要放什么再建；行与权限点都保留，
-- 之后把 deleted_flag 改回 FALSE 即可恢复，不需要重新播种。

UPDATE xsy_v2.t_menu
SET deleted_flag = TRUE,
    update_time = now()
WHERE deleted_flag = FALSE
  AND (menu_id IN (1100, 2000) OR parent_id IN (1100, 2000));
