package net.lab1024.sa.admin.module.scm.purchase;

import cn.dev33.satoken.annotation.SaCheckPermission;
import net.lab1024.sa.admin.module.scm.common.ScmW5PgITBase;
import net.lab1024.sa.admin.module.scm.purchase.controller.PurchaseDemandController;
import net.lab1024.sa.admin.module.scm.purchase.controller.PurchaseOrderController;
import net.lab1024.sa.admin.module.scm.purchase.controller.PurchaseReceiptController;
import net.lab1024.sa.admin.module.scm.warehouse.controller.WarehouseController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W5 权限种子（W5 Target Design §7.10 / §11.2，2 例）。
 *
 * <p>V16 是**只追加**的迁移：25 条 `t_menu`（701–706 目录/页面 + 711–713 + 721–727 +
 * 731–735 + 741 + 751–753 权限点）全部授权给 `role_id = 1`，并把 `t_menu` 的序列推到 `max + 1`。
 *
 * <p><b>为什么第 2 例（代码与菜单一致性）比第 1 例更重要</b>：
 * 菜单种子写错只是「前端少一个按钮」，但 **Controller 上写了一个菜单里没有的权限码**
 * 会让该接口对**所有人**都不可用（Sa-Token 找不到授权即拒绝），而且没有任何编译期信号。
 * 反过来，菜单里有、代码里没人用只是冗余。所以这里断言的方向是
 * 「**代码里出现的每个权限码都必须在种子中**」。
 */
@DisplayName("W5 权限种子：V16 菜单 / 授权 / 代码一致性（PG IT）")
class ScmPurchasePermissionMigrationIT extends ScmW5PgITBase {

    /** V16 播种的 25 个菜单 id。 */
    private static final List<Long> W5_MENU_IDS = List.of(
            701L, 702L, 703L, 704L, 705L, 706L,
            711L, 712L, 713L,
            721L, 722L, 723L, 724L, 725L, 726L, 727L,
            731L, 732L, 733L, 734L, 735L,
            741L,
            751L, 752L, 753L);

    private static String menuIdList() {
        return W5_MENU_IDS.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    // ------------------------------------------------------------------
    // 1. 菜单与授权
    // ------------------------------------------------------------------

    @Test
    @DisplayName("V16：25 条菜单全部就位、层级正确、全部授权给 role_id=1、序列已推进")
    void menuSeedIsCompleteAndGrantedToRoleOne() {
        // 25 条都在，且都可见
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM t_menu WHERE menu_id = ANY (string_to_array(?, ',')::bigint[])",
                Integer.class, menuIdList())).isEqualTo(25);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM t_menu WHERE menu_id = ANY (string_to_array(?, ',')::bigint[]) "
                        + "AND visible_flag = TRUE", Integer.class, menuIdList())).isEqualTo(25);

        // 层级：701 是根目录，702–706 挂在 701 下，权限点挂在各自页面下
        assertThat(jdbc.queryForObject(
                "SELECT parent_id FROM t_menu WHERE menu_id = 701", Long.class)).isZero();
        assertThat(jdbc.queryForList(
                "SELECT menu_id FROM t_menu WHERE parent_id = 701 AND menu_id BETWEEN 702 AND 706 "
                        + "ORDER BY menu_id", Long.class)).containsExactly(702L, 703L, 704L, 705L, 706L);
        assertThat(jdbc.queryForObject(
                "SELECT parent_id FROM t_menu WHERE menu_id = 712", Long.class)).isEqualTo(702L);
        assertThat(jdbc.queryForObject(
                "SELECT parent_id FROM t_menu WHERE menu_id = 726", Long.class)).isEqualTo(703L);
        assertThat(jdbc.queryForObject(
                "SELECT parent_id FROM t_menu WHERE menu_id = 735", Long.class)).isEqualTo(704L);
        assertThat(jdbc.queryForObject(
                "SELECT parent_id FROM t_menu WHERE menu_id = 741", Long.class)).isEqualTo(705L);
        assertThat(jdbc.queryForObject(
                "SELECT parent_id FROM t_menu WHERE menu_id = 753", Long.class)).isEqualTo(706L);

        // 目录与页面菜单没有权限码；权限点菜单有（perms_type = 1）
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM t_menu WHERE menu_id BETWEEN 701 AND 706 AND api_perms IS NOT NULL",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM t_menu WHERE menu_id = ANY (string_to_array(?, ',')::bigint[]) "
                        + "AND menu_id > 706 AND perms_type = 1 AND api_perms IS NOT NULL",
                Integer.class, menuIdList())).isEqualTo(19);

        // 全部授权给 role_id = 1
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM t_role_menu WHERE role_id = 1 "
                        + "AND menu_id = ANY (string_to_array(?, ',')::bigint[])",
                Integer.class, menuIdList())).isEqualTo(25);

        // 序列已推进到 max + 1：下一个 menu_id 不会撞已有行
        assertThat(jdbc.queryForObject(
                "SELECT nextval(pg_get_serial_sequence('t_menu','menu_id')) > 753", Boolean.class)).isTrue();
    }

    // ------------------------------------------------------------------
    // 2. 代码与菜单一致性
    // ------------------------------------------------------------------

    @Test
    @DisplayName("每个 @SaCheckPermission 权限码都必须在 t_menu.api_perms 中（代码与菜单不脱节）")
    void everyControllerPermissionExistsInMenuSeed() {
        Set<String> declared = new LinkedHashSet<>();
        int endpointCount = 0;
        for (Class<?> controller : List.of(PurchaseDemandController.class, PurchaseOrderController.class,
                PurchaseReceiptController.class, WarehouseController.class)) {
            for (Method method : controller.getDeclaredMethods()) {
                if (!Modifier.isPublic(method.getModifiers())) {
                    continue;
                }
                SaCheckPermission permission = method.getAnnotation(SaCheckPermission.class);
                assertThat(permission)
                        .as("%s#%s 必须声明 @SaCheckPermission", controller.getSimpleName(), method.getName())
                        .isNotNull();
                declared.addAll(List.of(permission.value()));
                endpointCount++;
            }
        }

        // 3（需求）+ 11（采购单）+ 8（收货）+ 5（仓库）= 27 个端点
        assertThat(endpointCount).isEqualTo(27);
        // 权限码去重后 19 个：3 + 8 + 5 + 3（多个端点共用同一个查询码）
        assertThat(declared).hasSize(19);

        List<String> seeded = jdbc.queryForList(
                "SELECT DISTINCT api_perms FROM t_menu WHERE api_perms IS NOT NULL", String.class);
        assertThat(seeded)
                .as("代码里声明的权限码必须都在 V16 种子里，否则接口对所有人不可用")
                .containsAll(declared);

        // 反向：采购域的权限码都带 scm: 前缀，不会误用系统菜单的权限
        assertThat(declared).allSatisfy(code -> assertThat(code).startsWith("scm:"));
    }
}
