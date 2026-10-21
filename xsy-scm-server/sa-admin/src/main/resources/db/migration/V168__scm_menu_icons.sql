-- 侧边栏二级菜单补齐图标。
--
-- 这批页面是分几轮加进来的，建菜单时 icon 留空。侧边栏里它们没有图标，而菜单项不会为空图标留位，
-- 于是同一组里有的行有图标、有的没有，行首文字左右差一个图标位，展开目录时一眼就能看出来。
-- 图标名存在数据库这一列，前端文案契约只扫源码，扫不到这里，只能靠迁移补。
--
-- 只填空值：以后有人在菜单管理里换过图标，再执行一遍也不会被覆盖。
-- 图标取 antd 的 Outlined 系列，与同组菜单保持同一套视觉语言（线性、16px、1em）。
UPDATE xsy_v2.t_menu AS m
SET icon = v.icon,
    update_time = now()
FROM (VALUES
    -- 销售订单：还在池子里、等着分配去向的订单
    (1261, 'PullRequestOutlined'),
    -- 采购：向多家供应商问价、比价
    (1880, 'DiffOutlined'),
    -- 库存：按批次存放的库存
    (2100, 'HddOutlined'),
    -- 库存：一物一码的包装码
    (2110, 'QrcodeOutlined'),
    -- 库存：包装码打错后的更正
    (2115, 'ToolOutlined'),
    -- 价格中心：随时段变动的时价
    (2121, 'ClockCircleOutlined'),
    -- 打印中心：可编辑的打印模板
    (1601, 'SnippetsOutlined'),
    -- 打印中心：已经打过的记录
    (1606, 'FileDoneOutlined'),
    -- 营销中心：限时活动
    (1701, 'ThunderboltOutlined'),
    -- 营销中心：优惠券
    (1705, 'PercentageOutlined'),
    -- 营销中心：商城首页版式
    (1850, 'LayoutOutlined')
) AS v(menu_id, icon)
WHERE m.menu_id = v.menu_id
  AND (m.icon IS NULL OR btrim(m.icon) = '');
