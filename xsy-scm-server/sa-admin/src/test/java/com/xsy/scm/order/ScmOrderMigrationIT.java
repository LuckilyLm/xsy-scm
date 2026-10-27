package com.xsy.scm.order;

import com.xsy.scm.common.ScmW3PgITBase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class ScmOrderMigrationIT extends ScmW3PgITBase {
    @Autowired
    Flyway flyway;
    static final String TABLES = "'xsy_sales_order','xsy_sales_order_item','xsy_order_address_snapshot','xsy_order_operation_log','xsy_idempotency_record','xsy_order_return','xsy_order_return_item','xsy_order_refund'";

    @Test
    void approvedSchemaHasEightTablesNoDeadFieldsAndNullableDraftPrices() {
        flyway.validate();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema=current_schema() AND table_name IN (" + TABLES + ")", Integer.class)).isEqualTo(8);
        // 25 条是订单域批准形态；V51 为报表日期轴追加 2 条部分索引（sales_order.confirmed_at、
        // order_refund.completed_at），二者都在本用例的表清单内；
        // V87（ADM-08 异常订单分析）再追加 order_return.rejected_at 的售后拒绝事件轴索引；
        // V126 为待分配订单池在 sales_order 上追加部分索引 idx_sales_order_unassigned_mall_pool；
        // 商城配送仓映射追加 idx_sales_order_mall_delivery_warehouse；
        // V184 为发车满 7 天自动锁单追加部分索引 idx_sales_order_auto_lock = 31。
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_indexes WHERE schemaname=current_schema() AND tablename IN (" + TABLES + ") AND indexname NOT LIKE '%_pkey'", Integer.class)).isEqualTo(31);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.sequences WHERE sequence_schema=current_schema() AND sequence_name IN ('sales_order_no_seq','order_return_no_seq','order_refund_no_seq')", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema=current_schema() AND table_name IN (" + TABLES + ") AND column_name IN ('fulfillment_status','pay_status','actual_weight')", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema=current_schema() AND table_name IN ('xsy_sales_order','xsy_sales_order_item') AND column_name IN ('draft_unit_price','draft_price_source','ordered_line_amount','ordered_total_amount') AND is_nullable='YES'", Integer.class)).isEqualTo(4);
        // 精度规则按 (表,列) 排除坐标列：按列名全局排除会让同名列在任何订单表上逃过金额精度校验
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema=current_schema() AND table_name IN (" + TABLES + ") AND data_type='numeric' AND (table_name,column_name) NOT IN (('xsy_order_address_snapshot','longitude'),('xsy_order_address_snapshot','latitude')) AND (numeric_precision<>18 OR numeric_scale<>4)", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='xsy_order_address_snapshot' AND data_type='numeric' AND (column_name,numeric_precision,numeric_scale) IN (('longitude',11,8),('latitude',10,8))", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_constraint WHERE connamespace=current_schema()::regnamespace AND contype='f' AND conrelid IN (SELECT oid FROM pg_class WHERE relname IN (" + TABLES + "))", Integer.class)).isZero();
    }

    @Test
    void permissionMenusGrantedAndBusinessLogsHaveNoMutationMapper() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM t_menu WHERE menu_id IN (601,602,603,604,605,611,612,613,614,615,616,617,618,619,621,622,623,624,625,631,632,641,642)", Integer.class)).isEqualTo(23);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM t_role_menu WHERE role_id=1 AND menu_id IN (601,602,603,604,605,611,612,613,614,615,616,617,618,619,621,622,623,624,625,631,632,641,642)", Integer.class)).isEqualTo(23);
        assertThat(jdbc.queryForObject("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname='ck_sales_order_source'", String.class)).contains("IMPORT");
        // 订单日志 DAO 只允许「追加 + 读」：listByOrderId 是商城订单时间线的只读入口，
        // 一旦出现 update / delete 方法，日志就不再是不可改的事实，这里会立刻变红。
        assertThat(com.xsy.scm.order.dao.OrderOperationLogDao.class.getMethods()).extracting(java.lang.reflect.Method::getName).containsExactlyInAnyOrder("insert", "query", "listByOrderId");
    }

    /**
     * V126 待分配订单池：菜单、权限发布、角色授权与候选查询的部分索引。
     *
     * <p>这一版只新增对象、不回填历史订单，所以断言的是「新对象存在且口径正确」。
     * 权限码必须与 {@code OrderPermission.ASSIGN} 一致：它硬编码在菜单迁移与 Java 常量两处，
     * 漏改任何一处都会让页面上的指派按钮点了 403。
     */
    @Test
    void unassignedOrderPoolMenuPermissionAndIndexArePublished() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM t_menu WHERE menu_id=1261 AND menu_name='待分配订单池' AND menu_type=2 AND parent_id=601 AND path='/order/unassigned-pool' AND visible_flag=TRUE", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT api_perms FROM t_menu WHERE menu_id=1262", String.class)).isEqualTo(com.xsy.scm.order.permission.OrderPermission.ASSIGN);
        assertThat(jdbc.queryForObject("SELECT web_perms FROM t_menu WHERE menu_id=1262", String.class))
                .as("api_perms 与 web_perms 必须一致，否则按钮显隐与后端拦截会分叉")
                .isEqualTo(com.xsy.scm.order.permission.OrderPermission.ASSIGN);
        assertThat(jdbc.queryForObject("SELECT context_menu_id FROM t_menu WHERE menu_id=1262", Long.class)).isEqualTo(1261L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM t_role_menu WHERE role_id=1 AND menu_id IN (1261,1262)", Integer.class)).as("超管必须看得到订单池").isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM t_role_menu rm JOIN t_role r ON r.role_id=rm.role_id WHERE r.role_code='SCM_SALES_LEAD' AND rm.menu_id IN (1261,1262)", Integer.class)).as("销售主管按 role_code 授权，两个菜单都要有").isEqualTo(2);

        // 候选查询是「MALL + 未分配 + 非终态」，索引必须把这个谓词下推成部分索引，
        // 否则待分配列表会退化成对整张订单表的顺序扫描。
        String definition = jdbc.queryForObject("SELECT indexdef FROM pg_indexes WHERE schemaname=current_schema() AND indexname='idx_sales_order_unassigned_mall_pool'", String.class);
        assertThat(definition).contains("order_source").contains("'MALL'").contains("seller_id IS NULL").contains("deleted = false").contains("DRAFT").contains("PENDING").contains("CONFIRMED");
    }

    /**
     * 操作日志的 operation_type：Java 枚举与库里的 CHECK 白名单必须逐项相等。
     *
     * <p>同一份取值硬编码在五处（业务枚举、DB CHECK、前端常量、查询表单 @Pattern、
     * 日志 operation_type 白名单），漏掉任何一处都要等第一次写日志才暴露。
     * 这里把最容易漏的两处钉成可执行的：加枚举不写迁移、或迁移加了取值而枚举没有，都会在这里变红。
     */
    @Test
    void operationLogTypeWhitelistMatchesJavaEnum() {
        String def = jdbc.queryForObject(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname='ck_order_operation_log_type'",
                String.class);
        var allowed = new java.util.HashSet<String>();
        var matcher = java.util.regex.Pattern.compile("'([A-Z_]+)'").matcher(def);
        while (matcher.find()) allowed.add(matcher.group(1));
        assertThat(allowed).containsExactlyInAnyOrderElementsOf(
                java.util.Arrays.stream(com.xsy.scm.order.constant.ScmOrderOperationTypeEnum.values())
                        .map(Enum::name).toList());
    }
}
