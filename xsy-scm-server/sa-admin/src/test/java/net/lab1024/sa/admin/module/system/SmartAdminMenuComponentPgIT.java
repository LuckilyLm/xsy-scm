package net.lab1024.sa.admin.module.system;

import net.lab1024.sa.admin.AdminApplication;
import net.lab1024.sa.admin.module.system.menu.domain.vo.MenuTreeVO;
import net.lab1024.sa.admin.module.system.menu.service.MenuService;
import net.lab1024.sa.admin.test.PgITPaths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 「已发布的页面菜单，其 {@code component} 必须指向真实存在的 Vue 文件」——全仓契约。
 *
 * <p><b>为什么需要这条门禁</b>：{@code src/router/index.ts} 用
 * {@code route.component = modules[relativePath]} 解析动态菜单，而 {@code modules} 来自
 * {@code import.meta.glob} 对 {@code ../views} 下全部 {@code .vue} 的扫描（构建期静态生成）。
 * 文件不存在时 {@code modules[relativePath]} 是 {@code undefined}，于是路由照样注册成功、
 * 菜单照样出现在侧栏，用户点进去才是一片空白 —— 构建、类型检查、后端测试全绿，
 * 没有任何一处会报错。把菜单种子与前端文件放进同一条断言，才能让这种漂移在 CI 就失败。
 *
 * <p>{@code visible_flag = false} 不是解法：它只映射到 {@code meta.hideInMenu}，
 * 路由与 {@code component} 依然注册，深链依然落到空白页。因此本契约不接受
 * 「先种菜单、用 visible_flag 藏起来、页面以后再补」这种做法 ——
 * 页面菜单必须与它的 {@code .vue} 同一阶段落库。
 *
 * <p><b>落库时机</b>：一次 migration 只发布它已经真实具备的能力。只有后端骨架（无 Controller）时
 * 既不种 action 权限，也不种页面菜单；action 权限随首个受保护 API 所在的迁移落库，
 * 页面菜单随页面实现落库。
 */
@SpringBootTest(classes = AdminApplication.class, properties = {
        "project.log-directory=" + PgITPaths.DEFAULT_LOG_DIR,
        "file.storage.local.upload-path=" + PgITPaths.DEFAULT_UPLOAD_PATH,
        "file.storage.local.url-prefix=http://127.0.0.1:18082",
        "logging.level.root=WARN"})
@DisplayName("页面菜单 component 存在性契约（PG IT）")
class SmartAdminMenuComponentPgIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MenuService menuService;

    /**
     * 隐藏的详情页路由必须能被「新建角色」授出去。
     *
     * <p>权限管理页的菜单树取 {@code MenuService.queryMenuTree}，它不过滤 {@code visible_flag}；
     * 勾选父菜单时前端会把子级一并勾上。因此隐藏详情路由只要挂在可见父菜单下，新角色勾了
     * 「线路管理」就同时拿到隐藏的「线路详情」，不需要迁移去复制角色权限。
     *
     * <p>一旦有人给菜单树加上 {@code visible_flag = TRUE} 过滤，这条链路会在「新建角色」上断掉，
     * 表现为能看列表、点详情空白，且迁移当时的角色仍然正常 —— 这里把它钉住。
     */
    @Test
    @DisplayName("隐藏的详情页路由出现在权限菜单树里，可随父菜单一起授出")
    void hiddenDetailRoutesStayGrantable() {
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM t_menu WHERE menu_id = 1004 AND menu_type = 2"
                        + " AND parent_id = 1001 AND visible_flag = FALSE AND deleted_flag = FALSE",
                Integer.class)).as("线路详情应当是线路管理下的隐藏页面菜单").isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM t_role_menu WHERE role_id = 1 AND menu_id = 1004",
                Integer.class)).as("迁移已把线路详情授给既有角色").isEqualTo(1);

        assertThat(containsMenu(menuService.queryMenuTree(false).getData(), 1004L))
                .as("权限菜单树若过滤 visible_flag，新建角色勾「线路管理」就拿不到隐藏的「线路详情」")
                .isTrue();
    }

    private boolean containsMenu(List<MenuTreeVO> nodes, Long menuId) {
        if (nodes == null) {
            return false;
        }
        return nodes.stream().anyMatch(node -> menuId.equals(node.getMenuId()) || containsMenu(node.getChildren(), menuId));
    }

    @Test
    @DisplayName("每个已发布页面菜单的 component 都能在 xsy-scm-web/src/views 下找到（既有缺口走显式基线）")
    void everyPublishedPageMenuResolvesToARealVueFile() {
        // menu_type = 2 是页面；frame_flag = TRUE 的外链菜单由 router 强制指向 iframe 组件，
        // 其 component 列不参与解析，因此排除，否则会产生与真实行为无关的失败。
        List<Map<String, Object>> pages = jdbc.queryForList(
                "SELECT menu_id, menu_name, component FROM t_menu "
                        + "WHERE menu_type = 2 AND component IS NOT NULL AND btrim(component) <> '' "
                        + "AND deleted_flag = FALSE AND COALESCE(frame_flag, FALSE) = FALSE "
                        + "ORDER BY menu_id");
        assertThat(pages).as("t_menu 里应当存在已发布的页面菜单，否则本用例形同虚设").isNotEmpty();

        Path views = viewsRoot();
        List<String> missing = pages.stream()
                .filter(row -> {
                    String component = String.valueOf(row.get("component"));
                    return !Files.exists(views.resolve(stripLeadingSlash(component)));
                })
                .map(row -> "menu_id=" + row.get("menu_id") + " 「" + row.get("menu_name") + "」 -> "
                        + row.get("component"))
                .toList();

        assertThat(missing)
                .as("已授权的页面菜单指向不存在的组件时，路由会注册成功但 component 为 undefined，"
                        + "用户点开是空白页且没有任何构建期/运行期报错。"
                        + "请让页面菜单与它的 .vue 同一阶段落库，或用新增 migration 下线已废弃菜单")
                .isEmpty();
    }

    @Test
    @DisplayName("Finance 五个页面菜单都指向已存在组件")
    void financePagesArePublishedWithTheirComponents() {
        // 能力点可以先行，但不能注册不存在的 .vue。
        assertThat(jdbc.queryForList(
                "SELECT menu_id FROM t_menu WHERE (menu_id BETWEEN 1500 AND 1599"
                        + " OR api_perms LIKE 'scm:finance:%' OR web_perms LIKE 'scm:finance:%'"
                        + " OR path LIKE '/finance/%') AND menu_type = 2 ORDER BY menu_id", Long.class))
                .as("F1-6 发布的只有五个应收、应付、收款、付款与核销页面")
                .containsExactly(1501L, 1502L, 1503L, 1504L, 1505L);

        assertThat(jdbc.queryForList(
                "SELECT menu_id FROM t_menu WHERE (menu_id BETWEEN 1500 AND 1599"
                        + " OR api_perms LIKE 'scm:finance:%' OR web_perms LIKE 'scm:finance:%')"
                        + " AND component IS NOT NULL ORDER BY menu_id", Long.class))
                .as("声明 component 的 Finance 菜单恰好就是五个真实页面")
                .containsExactly(1501L, 1502L, 1503L, 1504L, 1505L);
    }

    private String stripLeadingSlash(String component) {
        return component.startsWith("/") ? component.substring(1) : component;
    }

    /**
     * 定位 {@code xsy-scm-web/src/views}。
     *
     * <p>Surefire 的 {@code user.dir} 是 {@code xsy-scm-server/sa-admin}，IDE 里单跑则可能是仓库根，
     * 因此逐级向上找。找不到就抛错 —— 静默返回一个不存在的路径会让上面所有断言
     * 「因为一切都是缺失的」而变红，或者更糟：让基线核对悄悄通过。
     */
    private Path viewsRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int depth = 0; depth < 4 && current != null; depth++) {
            Path candidate = current.resolve("xsy-scm-web/src/views");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            Path nested = current.resolve("../xsy-scm-web/src/views").normalize();
            if (Files.isDirectory(nested)) {
                return nested;
            }
            current = current.getParent();
        }
        throw new IllegalStateException(
                "找不到 xsy-scm-web/src/views（user.dir = " + System.getProperty("user.dir") + "）");
    }
}
