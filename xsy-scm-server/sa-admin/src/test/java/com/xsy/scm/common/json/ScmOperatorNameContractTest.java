package com.xsy.scm.common.json;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 响应 VO 里「操作人字段」必须走人名序列化的静态契约。
 *
 * <p>
 * 操作人在库里存的是 {@code userType:userId}（见 {@code ScmOperator.current()}），直接序列化出去就是
 * {@code 1:1}。字段名散落在 30 多个 VO 上（{@code operator} / {@code createdBy} / {@code auditor} /
 * {@code shippedBy} …），漏一个就在页面上露一次原始 id。
 *
 * <p>
 * <b>为什么钉成静态扫描</b>：这类缺陷没有任何编译期或运行期信号 —— 页面照常渲染、接口照常返回成功，
 * 只是用户看到一串看不懂的数字；只有人一页页翻过去才会发现。名字集合来自对库的实测
 * （扫所有字符串列里真正出现过 {@code ^\d+:\d+$} 的列名），所以新增同类字段时这里会当场失败。
 *
 * <p>
 * <b>不是 Spring 测试</b>：只读源码文件，跑得比 IT 快得多，也不依赖数据库。
 */
class ScmOperatorNameContractTest {

    /** 字段名即操作人：{@code operator} / {@code auditor} 是固定名，其余一律以 {@code By} 结尾。 */
    private static final Pattern OPERATOR_FIELD = Pattern.compile(
            "^\\s*private String (operator|auditor|[a-z][a-zA-Z]*By);$");

    /** 审计快照：操作人藏在 Map 的键上，走的是另一个序列化器。 */
    private static final Pattern SNAPSHOT_FIELD = Pattern.compile(
            "^\\s*private Map<String, Object> (beforeData|afterData);$");

    private static final String OP_ANNOTATION = "@JsonSerialize(using = ScmOperatorNameSerializer.class)";

    private static final String SNAPSHOT_ANNOTATION = "@JsonSerialize(using = ScmOperatorSnapshotSerializer.class)";

    @Test
    @DisplayName("每个操作人字段都带人名序列化注解，否则页面上会露出 userType:userId")
    void everyOperatorFieldCarriesTheNameSerializer() throws IOException {
        List<String> offenders = new ArrayList<>();
        int checked = 0;

        for (Path path : voFiles()) {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (OPERATOR_FIELD.matcher(line).matches()) {
                    checked++;
                    if (!hasAnnotation(lines, i, OP_ANNOTATION)) {
                        offenders.add(relative(path) + " -> " + line.trim());
                    }
                }
                if (SNAPSHOT_FIELD.matcher(line).matches()) {
                    checked++;
                    if (!hasAnnotation(lines, i, SNAPSHOT_ANNOTATION)) {
                        offenders.add(relative(path) + " -> " + line.trim());
                    }
                }
            }
        }

        assertThat(checked).as("没扫到任何操作人字段，说明扫描规则或目录已经失效").isGreaterThan(20);
        assertThat(offenders)
                .as("这些字段会直接输出 userType:userId，请加 @JsonSerialize(using = ScmOperatorNameSerializer.class)")
                .isEmpty();
    }

    /** 注解必须紧贴在字段上一行（中间不能隔空行，否则读代码的人看不出它管的是哪个字段）。 */
    private static boolean hasAnnotation(List<String> lines, int fieldIndex, String annotation) {
        return fieldIndex > 0 && lines.get(fieldIndex - 1).trim().equals(annotation);
    }

    private static List<Path> voFiles() throws IOException {
        Path root = moduleRoot().resolve("src/main/java/com/xsy/scm");
        assertThat(root).as("SCM 源码目录必须存在，否则本断言形同虚设").exists();
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> p.getParent() != null && "vo".equals(p.getParent().getFileName().toString()))
                    .toList();
        }
    }

    private static String relative(Path path) {
        return moduleRoot().relativize(path).toString().replace('\\', '/');
    }

    private static Path moduleRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int depth = 0; depth < 4 && current != null; depth++) {
            if (Files.isDirectory(current.resolve("src/main/java/com/xsy/scm"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("找不到 sa-admin 模块目录（user.dir = " + System.getProperty("user.dir") + "）");
    }
}
