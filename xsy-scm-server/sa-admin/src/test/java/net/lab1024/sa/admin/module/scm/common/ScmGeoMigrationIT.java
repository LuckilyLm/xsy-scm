package net.lab1024.sa.admin.module.scm.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V40 地图 M0 的落地验证：区划字典种子、主档地理列约束，以及**存量地址保守解析规则本身**。
 *
 * <p>三件事都值得单独钉，因为它们一旦漂移就是静默的：
 * 字典少一个市 → 那个市的气泡从此画不出来（接口不报错，图上就是没有）；
 * 坐标约束松一格 → 半套坐标落库，M2 选点无从解释；
 * 解析规则放宽 → 把「长沙路」认成「长沙市」，地理数据被污染且没人知道哪些行是错的。
 *
 * <p>解析段**不复制到测试里**：直接从 classpath 读 V40 原文的 {@code -- Step 4} 之后整段执行，
 * 被测的就是上线的那段 SQL（沿用 {@link ScmW6PgITBase} 对 V19 backfill 的做法）。
 */
@DisplayName("地图 M0 地理数据地基（V40 PG IT）")
class ScmGeoMigrationIT extends ScmW6PgITBase {

    private static final String V40 = "V40__scm_geo_region_and_master_location.sql";

    private static final List<String> MASTER_TABLES = List.of("warehouse", "customer", "supplier");

    /**
     * V42 只给这三张表加了 {@code ck_…_location_complete}：配送链路用到的是仓库、客户与订单地址快照。
     * supplier 没有这条聚合约束，V40 的细粒度五条是它唯一的地理守卫 —— 所以负向用例的可接受拒绝原因
     * 逐表不同，不能统一按「细粒度或聚合」两个名字判。
     */
    private static final List<String> LOCATION_COMPLETE_TABLES =
            List.of("warehouse", "customer", "order_address_snapshot");

    // ------------------------------------------------------------------
    // 字典种子
    // ------------------------------------------------------------------

    @Test
    @DisplayName("区划字典：省市两级齐备、每个市都有质心、父子关系闭合")
    void regionSeedIsTwoLevelCompleteAndCentroidBearing() {
        assertThat(count("SELECT count(*) FROM scm_region "
                + "WHERE deleted = FALSE AND region_level = 1")).isEqualTo(34);
        assertThat(count("SELECT count(*) FROM scm_region "
                + "WHERE deleted = FALSE AND region_level = 2")).isEqualTo(414);

        // 气泡坐标完全来自质心：任何一行为空都会让那个市从地图上消失，且不产生任何错误
        assertThat(count("SELECT count(*) FROM scm_region "
                + "WHERE deleted = FALSE AND (center_lng IS NULL OR center_lat IS NULL)")).isZero();
        // 取值域按中国陆地范围放宽到含南海诸岛：越界即坐标写错，不是数据缺省
        assertThat(count("SELECT count(*) FROM scm_region WHERE deleted = FALSE "
                + "AND (center_lng NOT BETWEEN 73 AND 136 OR center_lat NOT BETWEEN 3 AND 54)")).isZero();

        assertThat(count("SELECT count(*) FROM scm_region c WHERE c.deleted = FALSE AND c.region_level = 2 "
                + "AND NOT EXISTS (SELECT 1 FROM scm_region p "
                + "WHERE p.deleted = FALSE AND p.region_level = 1 AND p.region_code = c.parent_code)")).isZero();
        // 没有下辖市的省会永远不着色；省名一旦被改名，map series 按名称就匹配不上了
        assertThat(count("SELECT count(*) FROM scm_region p WHERE p.deleted = FALSE AND p.region_level = 1 "
                + "AND NOT EXISTS (SELECT 1 FROM scm_region c "
                + "WHERE c.deleted = FALSE AND c.parent_code = p.region_code)")).isZero();

        assertThat(count("SELECT count(*) FROM (SELECT region_code FROM scm_region "
                + "WHERE deleted = FALSE GROUP BY region_code HAVING count(*) > 1) t")).isZero();
    }

    /**
     * 钉住「市名在全国不唯一」这一事实。
     *
     * <p>V40 解析规则里「市名不唯一时必须同时命中省名」那条分支不是防御性冗余 ——
     * 种子数据里确实存在重名市。断言它**只有**重庆市，是为了让重名清单变化时立刻暴露：
     * 届时消歧规则需要重新评估，而不是悄悄失守或变成死代码。
     */
    @Test
    @DisplayName("区划字典：市级重名只有重庆市，解析必须靠省名消歧")
    void duplicatedCityNamesAreExactlyTheDocumentedOne() {
        List<String> duplicated = jdbc.queryForList(
                "SELECT region_name || '=' || count(*) FROM scm_region "
                        + "WHERE deleted = FALSE AND region_level = 2 "
                        + "GROUP BY region_name HAVING count(*) > 1", String.class);

        assertThat(duplicated).containsExactly("重庆市=2");
        assertThat(jdbc.queryForList(
                "SELECT region_code FROM scm_region WHERE deleted = FALSE AND region_level = 2 "
                        + "AND region_name = '重庆市' ORDER BY region_code", Integer.class))
                .containsExactly(500100, 500200);
    }

    // ------------------------------------------------------------------
    // 主档地理列约束
    // ------------------------------------------------------------------

    /**
     * 三张主档共用同一组 CHECK（由 {@code format()} 批量建出），逐表验证一次，
     * 防止「只给一张表加了约束」这种只在另两张表写入时才暴露的缺陷。
     *
     * <p>每个期望失败的语句都包在 SAVEPOINT 里：PostgreSQL 在报错后会把**整个事务**置为
     * aborted，不回滚到保存点就无法在同一用例里继续验证下一条约束和「合法值必须写得进」。
     */
    @Test
    @DisplayName("主档地理列：半套坐标、越界值与未知 CRS 一律被 DB 拒绝")
    void masterGeoColumnsRejectHalfCoordinatesAndUnknownCrs() {
        for (String table : MASTER_TABLES) {
            Long id = newMasterRow(table);

            // 只有经度没有纬度：既没法画也没法解释，M2 选点拿到这种行无从判断是否被截断过
            assertRejectedBy("半套坐标", rejectReasons(table, "coordinate_pair"),
                    "UPDATE " + table + " SET longitude = 120.155078 WHERE id = ?", id);

            // CRS 只能伴随坐标存在：标了 GCJ02 却没坐标，等于声明了一个无法验证的口径
            assertRejectedBy("带坐标系却没坐标", rejectReasons(table, "crs_with_coordinate"),
                    "UPDATE " + table + " SET geom_crs = 'GCJ02' WHERE id = ?", id);

            // 白名单之外的大小写变体必须进不来，否则两套坐标系会伪装成一套
            assertRejectedBy("CRS 大小写变体", rejectReasons(table, "geom_crs"),
                    "UPDATE " + table + " SET longitude = 120.155078, latitude = 30.259244, "
                            + "geom_crs = 'gcj-02' WHERE id = ?", id);

            // 其余字段全合法，只让经度越界：拒绝原因才能归到取值域本身，而不是缺了 CRS
            assertRejectedBy("经度越界", rejectReasons(table, "longitude"),
                    "UPDATE " + table + " SET longitude = 181, latitude = 30.259244, "
                            + "geom_crs = 'GCJ02' WHERE id = ?", id);

            assertRejectedBy("纬度越界", rejectReasons(table, "latitude"),
                    "UPDATE " + table + " SET longitude = 120.155078, latitude = 91, "
                            + "geom_crs = 'GCJ02' WHERE id = ?", id);

            // 成对 + 白名单 + 域内：这条必须写得进，否则上面的拒绝只是「列根本没法用」
            assertThat(jdbc.update("UPDATE " + table + " SET longitude = 120.155078, latitude = 30.259244, "
                    + "geom_crs = 'GCJ02' WHERE id = ?", id)).isEqualTo(1);
        }
    }

    /**
     * 逐表钉住 V40 的细粒度地理 CHECK 与 V42 的聚合 CHECK 都还在。
     *
     * <p>负向用例只能断言「拒绝出自这几条约束之一」，所以某条约束被删掉不会让那里变红，
     * 必须由这里保证：细粒度约束一旦消失，规则就只剩聚合约束一份，而它并不按列拆分原因。
     */
    @Test
    @DisplayName("主档地理约束：V40 细粒度五条逐表齐备，V42 聚合约束按配送链路分布")
    void geoCheckConstraintsExistOnEveryMaster() {
        for (String table : MASTER_TABLES) {
            List<String> present = jdbc.queryForList(
                    "SELECT con.conname FROM pg_constraint con "
                            + "JOIN pg_class c ON c.oid = con.conrelid "
                            + "JOIN pg_namespace n ON n.oid = c.relnamespace "
                            + "WHERE con.contype = 'c' AND n.nspname = current_schema() AND c.relname = ?",
                    String.class, table);

            assertThat(present).as("%s 的 V40 细粒度地理 CHECK 缺失", table).contains(
                    "ck_" + table + "_geom_crs",
                    "ck_" + table + "_longitude",
                    "ck_" + table + "_latitude",
                    "ck_" + table + "_coordinate_pair",
                    "ck_" + table + "_crs_with_coordinate");

            // 聚合约束决定负向用例允许哪些拒绝原因，所以它的有无也要钉住，不能只靠常量集合自证
            String umbrella = "ck_" + table + "_location_complete";
            if (LOCATION_COMPLETE_TABLES.contains(table)) {
                assertThat(present).as("%s 缺少 V42 聚合约束 %s", table, umbrella).contains(umbrella);
            } else {
                assertThat(present).as("%s 多出 V42 聚合约束 %s", table, umbrella).doesNotContain(umbrella);
            }
        }
    }

    @Test
    @DisplayName("主档按市聚合走 city_code 部分索引，且只覆盖活动且已归属的行")
    void cityCodePartialIndexExistsOnEveryMaster() {
        for (String table : MASTER_TABLES) {
            List<String> definitions = jdbc.queryForList(
                    "SELECT indexdef FROM pg_indexes WHERE schemaname = current_schema() "
                            + "AND tablename = ? AND indexdef LIKE '%city_code%'", String.class, table);

            assertThat(definitions)
                    .as("%s 缺少按市聚合的部分索引：大屏 geo 聚合会退化成全表扫", table)
                    .anyMatch(def -> def.contains("deleted") && def.contains("city_code IS NOT NULL"));
        }
    }

    // ------------------------------------------------------------------
    // 存量地址保守解析
    // ------------------------------------------------------------------

    /**
     * 重放 V40 Step 4，逐条验证「宁缺勿错」的规则。
     *
     * <p>用例包在事务里、结束回滚，所以可以在真实字典上重放整段 UPDATE 而不污染库。
     */
    @Test
    @DisplayName("存量地址解析：只认完整市名、重名靠省名消歧、解析不出留 NULL、幂等不覆盖")
    void conservativeAddressParseMatchesOnlyFullCityNames() {
        Long fullAddress = newCustomer();
        Long streetTrap = newCustomer();
        Long ambiguousCity = newCustomer();
        Long provinceOnly = newCustomer();
        Long unparseable = newCustomer();

        address(fullAddress, "浙江省杭州市西湖区文三路100号");
        // 「长沙路」是街道，不是长沙市的证据；同一串里的「上海市」才是
        address(streetTrap, "上海市浦东新区长沙路88号");
        address(ambiguousCity, "重庆市渝北区龙溪街道1号");
        address(provinceOnly, "吉林省某区某街1号");
        address(unparseable, "某某工业园 A 栋 3 层");

        runConservativeParseSection();

        assertThat(geoColumnsOf(fullAddress)).containsExactly(330000, 330100, "浙江省", "杭州市");
        assertThat(geoColumnsOf(streetTrap)).containsExactly(310000, 310100, "上海市", "上海市");
        // 两条同名市里取编码较小者：并列取小才能让同一地址重放两次得到同一个结果
        assertThat(geoColumnsOf(ambiguousCity)).containsExactly(500000, 500100, "重庆市", "重庆市");
        // 只解析到市：市解析不出时至少把省补齐，让省级统计不丢行
        assertThat(geoColumnsOf(provinceOnly)).containsExactly(220000, null, "吉林省", null);
        assertThat(geoColumnsOf(unparseable)).containsExactly(null, null, null, null);

        // 幂等：解析段只碰 city_code IS NULL 的行，已人工归属的绝不覆写
        assertThat(jdbc.update("UPDATE customer SET province_code = 440000, city_code = 440100 WHERE id = ?",
                unparseable)).isEqualTo(1);
        runConservativeParseSection();
        assertThat(geoColumnsOf(unparseable)).containsExactly(440000, 440100, null, null);
    }

    @Test
    @DisplayName("解析只到市：区县由人工选择维护，解析段不碰区县两列")
    void districtCodeIsNeverTouchedByTheParse() {
        Long customerId = newCustomer();
        address(customerId, "浙江省杭州市余杭区文一西路969号");
        assertThat(jdbc.update("UPDATE customer SET district_code = 330110, district_name = '余杭区' "
                + "WHERE id = ?", customerId)).isEqualTo(1);

        runConservativeParseSection();

        assertThat(geoColumnsOf(customerId)).containsExactly(330000, 330100, "浙江省", "杭州市");
        assertThat(jdbc.queryForObject(
                "SELECT district_code || '/' || district_name FROM customer WHERE id = ?",
                String.class, customerId)).isEqualTo("330110/余杭区");
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    /**
     * 执行 V40 的解析段原文。
     *
     * <p>取 {@code -- Step 4} 到文件末尾（它是文件的最后一段）；标记被改名时索引返回 -1，
     * 断言立刻失败，不会静默跳过被测 SQL。
     */
    private void runConservativeParseSection() {
        String sql = migrationSql(V40);
        int from = sql.indexOf("-- Step 4");
        assertThat(from).as("%s 中找不到 -- Step 4 段（标记被改名了？）", V40).isGreaterThanOrEqualTo(0);
        jdbc.execute(sql.substring(from));
    }

    /**
     * 在保存点里执行一条期望被拒绝的语句，随后回滚保存点以保持事务可用。
     *
     * <p>仍然断言拒绝出自哪条约束：只判「抛异常」的话，列名写错、语法错误同样会拒绝，
     * 用例会在约束被删掉后依然绿着。但允许的是名字集合而不是单个名字 —— V42 的
     * {@code ck_…_location_complete} 把成对、取值域与 CRS 白名单整体重写了一遍，
     * 同一行往往同时违反细粒度约束和该聚合约束，而 PostgreSQL 只报它先求值到的那条。
     * 细粒度约束本身是否还在，由 {@link #geoCheckConstraintsExistOnEveryMaster()} 钉住。
     */
    private void assertRejectedBy(String rule, List<String> allowed, String sql, Object... args) {
        jdbc.execute("SAVEPOINT geo_case");
        String message = null;
        try {
            jdbc.update(sql, args);
        } catch (DataAccessException e) {
            message = rootMessage(e);
        }
        jdbc.execute("ROLLBACK TO SAVEPOINT geo_case");
        String rejection = message;
        assertThat(rejection).as("期望按「%s」被拒绝，实际未被拒绝", rule).isNotNull();
        assertThat(allowed).as("拒绝原因不属于「%s」的任何一条约束，实际: %s", rule, rejection)
                .anyMatch(rejection::contains);
    }

    /** 某条细粒度规则的可接受拒绝原因：它自己，加上（该表有的话）覆盖同一条规则的 V42 聚合约束。 */
    private static List<String> rejectReasons(String table, String constraint) {
        List<String> allowed = new ArrayList<>(List.of("ck_" + table + "_" + constraint));
        if (LOCATION_COMPLETE_TABLES.contains(table)) {
            allowed.add("ck_" + table + "_location_complete");
        }
        return allowed;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage();
    }

    /**
     * 三张主档各取一条新建行（走已验收的服务写入口，不直插表）。
     */
    private Long newMasterRow(String table) {
        return switch (table) {
            case "warehouse" -> newWarehouse("GEO-W");
            case "supplier" -> newSupplier("GEO-S");
            default -> newCustomer();
        };
    }

    private void address(Long customerId, String value) {
        assertThat(jdbc.update("UPDATE customer SET address = ?, province_code = NULL, province_name = NULL, "
                + "city_code = NULL, city_name = NULL WHERE id = ?", value, customerId)).isEqualTo(1);
        evictMybatisCache();
    }

    /**
     * 归属四元组：省码 / 市码 / 省名 / 市名；null 原样保留，用来断言「没解析出来」。
     */
    private List<Object> geoColumnsOf(Long customerId) {
        evictMybatisCache();
        Map<String, Object> row = jdbc.queryForList(
                "SELECT province_code, city_code, province_name, city_name FROM customer WHERE id = ?",
                customerId).stream().findFirst().orElseThrow();
        return Arrays.asList(row.get("province_code"), row.get("city_code"),
                row.get("province_name"), row.get("city_name"));
    }

    private int count(String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }
}
