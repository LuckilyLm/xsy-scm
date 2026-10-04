package com.xsy.scm.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 页面菜单必须指向真实存在的前端组件（PG IT）。
 *
 * <p><b>为什么要钉在库级契约上</b>：路由注册是 {@code route.component = modules[relativePath]}，
 * {@code relativePath} 取的是 {@code t_menu.component}；文件缺失时它静默变成 {@code undefined}，
 * 菜单点开是空白页，而构建、类型检查与后端测试全部照绿。F1-1 那轮就是这样漏出去过一次，
 * 所以这条不能只写在文档里。
 *
 * <p>外链型菜单（{@code component} 是 http 地址，例如 swagger）不参与文件系统检查。
 */
class ScmMenuComponentExistencePgIT extends ScmW2PgITBase {

    /** 从测试工作目录向上找到仓库根（含 {@code xsy-scm-web/src/views} 的那一层）。 */
    private static Path webViewsRoot() {
        Path cursor = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 6 && cursor != null; depth++, cursor = cursor.getParent()) {
            Path candidate = cursor.resolve("xsy-scm-web").resolve("src").resolve("views");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "未找到 xsy-scm-web/src/views：本用例必须在仓库检出内运行，否则菜单-组件一致性无法校验");
    }

    @Test
    @DisplayName("未删除的页面菜单都指向真实存在的 .vue 组件")
    void everyMenuComponentExistsOnDisk() {
        Path views = webViewsRoot();
        List<String> components = jdbc.queryForList(
                "SELECT DISTINCT component FROM t_menu"
                        + " WHERE deleted_flag = FALSE AND component IS NOT NULL AND component <> ''"
                        + "   AND component NOT LIKE 'http%'"
                        + " ORDER BY component", String.class);

        List<String> missing = new ArrayList<>();
        for (String component : components) {
            String relative = component.replace('\\', '/');
            if (relative.startsWith("/")) {
                relative = relative.substring(1);
            }
            String stem = relative.endsWith(".vue")
                    ? relative.substring(0, relative.length() - 4) : relative;
            if (!Files.isRegularFile(views.resolve(stem + ".vue"))
                    && !Files.isRegularFile(views.resolve(stem).resolve("index.vue"))) {
                missing.add(component);
            }
        }

        assertThat(missing)
                .as("以下页面菜单指向的组件文件不存在，点开必是空白页（构建/类型检查/后端测试都发现不了）")
                .isEmpty();
        assertThat(components).as("扫描到的组件清单不应为空，否则本用例是空跑").isNotEmpty();
    }
}
