package net.lab1024.sa.admin.module.system;

import net.lab1024.sa.admin.AdminApplication;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 「已发布的页面菜单，其 {@code component} 必须指向真实存在的 Vue 文件」——全仓契约。
 *
 * <p><b>为什么需要这条门禁</b>：{@code src/router/index.ts} 用
 * {@code route.component = modules[relativePath]} 解析动态菜单，而 {@code modules} 来自
 * {@code import.meta.glob} 对 {@code ../views} 下全部 {@code .vue} 的扫描（构建期静态生成）。
 * 文件不存在时 {@code modules[relativePath]} 是 {@code undefined}，于是路由**照样注册成功**、
 * 菜单**照样出现在侧栏**，用户点进去才是一片空白 —— 构建、类型检查、后端测试全绿，
 * 没有任何一处会报错。把菜单种子与前端文件放进同一条断言，才能让这种漂移在 CI 就失败。
 *
 * <p>{@code visible_flag = false} **不是**解法：它只映射到 {@code meta.hideInMenu}，
 * 路由与 {@code component} 依然注册，深链依然落到空白页。因此本契约不接受
 * 「先种菜单、用 visible_flag 藏起来、页面以后再补」这种做法 ——
 * 页面菜单必须与它的 {@code .vue} 同一阶段落库。
 *
 * <p><b>阶段纪律</b>：一个阶段只发布它已经真实具备的能力。后端骨架阶段（无 Controller）
 * 既不种 action 权限，也不种页面菜单；action 权限随首个受保护 API 所在阶段的迁移落库，
 * 页面菜单随页面实现的阶段落库。Finance R1 的 F1-1 即按此办理（V65 是纯 DDL）。
 *
 * <p><b>基线只减不增</b>：{@link #LEGACY_MISSING_COMPONENTS} 是本门禁**加入之前**就已存在的缺口，
 * 逐条注明出处与处置。新增缺口一律让本用例失败；补上页面或下线菜单后，从基线里删掉对应条目。
 * 与 {@code SmartAdminMapperPgValidationIT} 的跳过项基线、{@code tools/ts_baseline_ratchet.py}
 * 是同一套「棘轮」取向。
 */
@SpringBootTest(classes = AdminApplication.class, properties = {
        "project.log-directory=" + PgITPaths.DEFAULT_LOG_DIR,
        "file.storage.local.upload-path=" + PgITPaths.DEFAULT_UPLOAD_PATH,
        "file.storage.local.url-prefix=http://127.0.0.1:18082",
        "logging.level.root=WARN"})
@DisplayName("页面菜单 component 存在性契约（PG IT）")
class SmartAdminMenuComponentPgIT {

    /**
     * 门禁加入前就已存在的缺口（2026-09-26 实测）。两处都**不是**本轮引入，且都不属于 Finance。
     *
     * <ul>
     *   <li>{@code /business/scm/customer/customer-sku-visibility-list.vue} —— V11 种的
     *       menu_id 435「客户 SKU 可见性」，仅授超管、{@code visible_flag = true}，
     *       但前端从未实现该页面（全仓 0 处引用）。V11 已应用不可改，
     *       修复要么新开一条 data-only 迁移下线该菜单，要么补页面 —— 两者都超出 Finance R1 F1-1 范围，
     *       已如实记入 {@code docs/progress.md} 的未覆盖项。</li>
     *   <li>{@code /support/demonstration/index.vue} —— V3 从 SmartAdmin 底座带进来的
     *       menu_id 85「组件演示」，V2 前端工作区没有搬运这个演示页。属底座示例菜单，非业务能力。</li>
     * </ul>
     */
    private static final Set<String> LEGACY_MISSING_COMPONENTS = Set.of(
            "/business/scm/customer/customer-sku-visibility-list.vue",
            "/support/demonstration/index.vue");

    @Autowired
    private JdbcTemplate jdbc;

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
                    return !LEGACY_MISSING_COMPONENTS.contains(component)
                            && !Files.exists(views.resolve(stripLeadingSlash(component)));
                })
                .map(row -> "menu_id=" + row.get("menu_id") + " 「" + row.get("menu_name") + "」 -> "
                        + row.get("component"))
                .toList();

        assertThat(missing)
                .as("已授权的页面菜单指向不存在的组件时，路由会注册成功但 component 为 undefined，"
                        + "用户点开是空白页且没有任何构建期/运行期报错。"
                        + "请让页面菜单与它的 .vue 同一阶段落库；"
                        + "若确属本门禁加入前的历史缺口，才允许加进 LEGACY_MISSING_COMPONENTS 并注明出处")
                .isEmpty();
    }

    @Test
    @DisplayName("基线里的既有缺口仍然缺失（补上页面或下线菜单后必须同步删除基线条目）")
    void legacyBaselineEntriesAreStillMissing() {
        Path views = viewsRoot();
        List<String> fixed = LEGACY_MISSING_COMPONENTS.stream()
                .filter(component -> Files.exists(views.resolve(stripLeadingSlash(component))))
                .toList();
        // 棘轮的另一个方向：条目一旦被修好却留在基线里，基线就会慢慢变成一张没人核对的豁免清单。
        assertThat(fixed)
                .as("这些组件已经存在，请从 LEGACY_MISSING_COMPONENTS 中删除对应条目")
                .isEmpty();
    }

    @Test
    @DisplayName("Finance R1 的 F1-1 没有留下任何「授权页面菜单 → component 不存在」的状态")
    void financeHasNoPublishedPageMenuInThisPhase() {
        // F1-1 只有 V65（纯 DDL），没有任何 Controller，也没有任何 .vue；
        // 因此财务段既不该有页面菜单，也不该有任何 scm:finance:* 权限串。
        assertThat(jdbc.queryForList(
                "SELECT menu_id FROM t_menu WHERE menu_id BETWEEN 1500 AND 1599 "
                        + "OR api_perms LIKE 'scm:finance:%' OR web_perms LIKE 'scm:finance:%' "
                        + "OR path LIKE '/finance/%'", Long.class))
                .as("Finance 的页面菜单随 F1-6 的 .vue 一起落库，action 权限随首个受保护 API 落库")
                .isEmpty();
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
