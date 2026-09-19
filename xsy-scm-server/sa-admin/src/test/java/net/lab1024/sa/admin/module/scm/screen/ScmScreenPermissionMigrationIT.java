package net.lab1024.sa.admin.module.scm.screen;

import cn.dev33.satoken.annotation.SaCheckPermission;
import net.lab1024.sa.admin.module.scm.common.ScmW6PgITBase;
import net.lab1024.sa.admin.module.scm.screen.controller.ScreenDataController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B7 数据大屏权限种子（V28）与代码一致性。
 *
 * <p>V28 是只追加的 data-only 迁移：900 目录 + 901 查询权限，全部授权给 role_id = 1，
 * 并推进 t_menu 序列。
 */
@DisplayName("B7 数据大屏权限种子：V28 菜单 / 授权 / 代码一致性（PG IT）")
class ScmScreenPermissionMigrationIT extends ScmW6PgITBase {

    private static final List<Long> SCREEN_MENU_IDS = List.of(900L, 901L);

    @Test
    @DisplayName("V28：菜单与授权就位，序列已推进")
    void menuSeedIsCompleteAndGrantedToRoleOne() {
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM t_menu WHERE menu_id IN (900, 901)",
                Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM t_menu WHERE menu_id = 901 AND perms_type = 1 "
                        + "AND api_perms = 'scm:screen:query'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM t_role_menu WHERE role_id = 1 AND menu_id IN (900, 901)",
                Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT nextval(pg_get_serial_sequence('t_menu','menu_id')) > 901",
                Boolean.class)).isTrue();
    }

    @Test
    @DisplayName("ScreenDataController 的每个权限码都必须在 t_menu.api_perms 中")
    void everyControllerPermissionExistsInMenuSeed() {
        Set<String> declared = new LinkedHashSet<>();
        for (Method method : ScreenDataController.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            SaCheckPermission permission = method.getAnnotation(SaCheckPermission.class);
            assertThat(permission)
                    .as("ScreenDataController#%s 必须声明 @SaCheckPermission", method.getName())
                    .isNotNull();
            declared.addAll(List.of(permission.value()));
        }
        for (String perm : declared) {
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM t_menu WHERE api_perms = ?",
                    Integer.class, perm))
                    .as("权限码 %s 不在 t_menu.api_perms 中", perm)
                    .isEqualTo(1);
        }
    }
}
