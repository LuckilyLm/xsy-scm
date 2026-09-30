package com.xsy.scm;

import cn.dev33.satoken.annotation.SaCheckPermission;
import net.lab1024.sa.admin.AdminApplication;
import com.xsy.scm.common.ScmW5PgITBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.RegexPatternTypeFilter;
import org.springframework.stereotype.Controller;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SCM Permission Catalog 与菜单发布契约（PG IT）")
class ScmPermissionContractPgIT extends ScmW5PgITBase {

    private static final String BASE_PACKAGE = "com.xsy.scm";
    private static final Pattern CATALOG_CLASS =
            Pattern.compile("com\\.xsy\\.scm\\.(?:.*\\.permission\\.[A-Za-z_$][\\w$]*Permission"
                    + "|report\\.constant\\.ScmReportPermission)");
    private static final Pattern PERMISSION_FIELD = Pattern.compile(
            "(?:([A-Za-z_$][\\w$]*Permission)|ScmDataScopeService)\\.([A-Z][A-Z0-9_]*)");
    private static final Pattern PERMISSION_ALIAS = Pattern.compile("[A-Z][A-Z0-9_]*");
    private static final Pattern STP_CHECK = Pattern.compile(
            "\\bStpUtil\\s*\\.\\s*(?:checkPermission|hasPermission)\\s*\\(([^)]*)\\)");
    private static final Pattern SCOPE_PERMISSION_CALL = Pattern.compile(
            "\\bScmDataScopeService\\s*\\.\\s*hasPermission\\s*\\(([^)]*)\\)");
    private static final Pattern LOCAL_SCOPE_PERMISSION_CALL = Pattern.compile(
            "(?<![.\\w])hasPermission\\s*\\((?!\\s*String\\s+permissionCode\\s*\\))([^)]*)\\)");
    private static final Pattern NON_CODE = Pattern.compile(
            "(?s)/\\*.*?\\*/|//[^\\r\\n]*|\"\"\".*?\"\"\"|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'");

    @Test
    @DisplayName("Controller、功能、数据范围及成本权限均来自目录并已发布到 t_menu.api_perms")
    void usedPermissionsAreCataloguedAndPublished() throws Exception {
        Map<String, Class<?>> catalogs = permissionCatalogClasses();
        Set<String> catalogValues = permissionCatalogValues(catalogs.values());
        Set<String> usedValues = new HashSet<>();
        int endpointCount = addControllerPermissions(usedValues);
        PermissionScan permissionScan = scanPermissionUsage(catalogs, catalogValues, usedValues);

        assertThat(endpointCount).as("SCM Controller endpoint scanner 必须命中实际端点").isPositive();
        assertThat(permissionScan.stpCalls()).as("StpUtil.checkPermission / hasPermission 必须被源码扫描")
                .isPositive();
        assertThat(permissionScan.scopeCalls()).as("ScmDataScopeService 权限包装调用必须被源码扫描")
                .isPositive();
        Set<String> uncataloguedValues = new HashSet<>(usedValues);
        uncataloguedValues.removeAll(catalogValues);
        assertThat(uncataloguedValues).as("每个 Controller / StpUtil 权限值必须属于正式 Java Catalog")
                .isEmpty();

        Set<String> publishedValues = jdbc.queryForList(
                        "SELECT DISTINCT api_perms FROM t_menu WHERE api_perms IS NOT NULL AND BTRIM(api_perms) <> ''",
                        String.class)
                .stream()
                .flatMap(value -> Arrays.stream(value.split(",")))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toSet());
        assertThat(publishedValues)
                .as("不得在测试中自动补种权限；每个真实端点权限必须由 migration 发布到 t_menu.api_perms")
                .containsAll(usedValues);
    }

    @Test
    @DisplayName("源码权限扫描忽略普通字符串和注释")
    void permissionSourceScannerIgnoresStringsAndComments() {
        String source = "// StpUtil.checkPermission(UnknownPermission.VALUE)\n"
                + "String example = \"StpUtil.hasPermission(UnknownPermission.VALUE)\";\n"
                + "StpUtil.hasPermission(OfficialPermission.VALUE);\n";
        Matcher checks = STP_CHECK.matcher(codeWithoutCommentsAndStrings(source));

        assertThat(checks.find()).isTrue();
        assertThat(checks.group(1).trim()).isEqualTo("OfficialPermission.VALUE");
        assertThat(checks.find()).isFalse();
    }

    private int addControllerPermissions(Set<String> usedValues) throws ClassNotFoundException {
        Set<Class<?>> controllers = scanClasses(new AnnotationTypeFilter(Controller.class));
        int endpointCount = 0;
        for (Class<?> controller : controllers) {
            SaCheckPermission classPermission = AnnotatedElementUtils.findMergedAnnotation(
                    controller, SaCheckPermission.class);
            for (Method method : controller.getMethods()) {
                if (AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class) == null) {
                    continue;
                }
                endpointCount++;
                addPermissionValues(classPermission, usedValues);
                addPermissionValues(AnnotatedElementUtils.findMergedAnnotation(method, SaCheckPermission.class),
                        usedValues);
            }
        }
        return endpointCount;
    }

    private PermissionScan scanPermissionUsage(Map<String, Class<?>> catalogs, Set<String> catalogValues,
            Set<String> usedValues) throws Exception {
        Path classesDirectory = Path.of(AdminApplication.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path sourceRoot = classesDirectory.getParent().getParent().resolve("src/main/java/com/xsy/scm");
        assertThat(sourceRoot).as("SCM source tree for direct permission call sites").isDirectory();

        int stpCallCount = 0;
        int scopeCallCount = 0;
        int reportAccessPermissionCalls = 0;
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            for (Path sourceFile : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String code = codeWithoutCommentsAndStrings(Files.readString(sourceFile, StandardCharsets.UTF_8));
                Matcher checks = STP_CHECK.matcher(code);
                while (checks.find()) {
                    String argument = checks.group(1).trim();
                    if (sourceFile.getFileName().toString().equals("ScmDataScopeService.java")
                            && argument.equals("permissionCode")) {
                        // This wrapper accepts a permission code; every actual caller is checked below.
                        continue;
                    }
                    stpCallCount++;
                    addPermissionArguments(checks.group(1), sourceFile, catalogs, catalogValues, usedValues);
                    if (sourceFile.getFileName().toString().equals("ScmReportAccess.java"))
                        reportAccessPermissionCalls++;
                }

                Matcher scopeCalls = SCOPE_PERMISSION_CALL.matcher(code);
                while (scopeCalls.find()) {
                    scopeCallCount++;
                    addPermissionArguments(scopeCalls.group(1), sourceFile, catalogs, catalogValues, usedValues);
                }
                if (sourceFile.getFileName().toString().equals("ScmDataScopeService.java")) {
                    Matcher localScopeCalls = LOCAL_SCOPE_PERMISSION_CALL.matcher(code);
                    while (localScopeCalls.find()) {
                        scopeCallCount++;
                        addPermissionArguments(localScopeCalls.group(1), sourceFile, catalogs, catalogValues,
                                usedValues);
                    }
                }
            }
        }
        assertThat(reportAccessPermissionCalls)
                .as("ScmReportAccess 必须直接读取正式 Catalog 中的成本权限")
                .isPositive();
        return new PermissionScan(stpCallCount, scopeCallCount);
    }

    private void addPermissionArguments(String arguments, Path sourceFile, Map<String, Class<?>> catalogs,
            Set<String> catalogValues, Set<String> usedValues) throws Exception {
        for (String argument : arguments.split(",")) {
            String reference = argument.trim();
            Matcher fieldReference = PERMISSION_FIELD.matcher(reference);
            String typeName;
            String fieldName;
            if (fieldReference.matches()) {
                typeName = fieldReference.group(1) == null
                        ? "ScmDataScopeService"
                        : fieldReference.group(1);
                fieldName = fieldReference.group(2);
            } else {
                boolean isScopeAlias = sourceFile.getFileName().toString().equals("ScmDataScopeService.java")
                        && PERMISSION_ALIAS.matcher(reference).matches();
                assertThat(isScopeAlias)
                        .as("SCM 权限检查必须引用正式 Permission Catalog 字段，而不是文本或计算表达式 in %s",
                                sourceFile)
                        .isTrue();
                typeName = "ScmDataScopeService";
                fieldName = reference;
            }
            Class<?> catalog = catalogs.get(typeName);
            if (catalog == null && typeName.equals("ScmDataScopeService")) {
                catalog = Class.forName("com.xsy.scm.common.scope.ScmDataScopeService",
                        false, Thread.currentThread().getContextClassLoader());
            }
            assertThat(catalog).as("Permission Catalog or approved scope wrapper %s must be discovered", typeName)
                    .isNotNull();
            Field field = catalog.getField(fieldName);
            int modifiers = field.getModifiers();
            assertThat(field.getType()).isEqualTo(String.class);
            assertThat(Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers))
                    .as("permission reference %s.%s must be a public static final String", typeName,
                            field.getName())
                    .isTrue();
            String value = (String) field.get(null);
            assertThat(catalogValues)
                    .as("effective permission %s.%s must resolve to a formal Permission Catalog value", typeName,
                            field.getName())
                    .contains(value);
            usedValues.add(value);
        }
    }

    private static String codeWithoutCommentsAndStrings(String source) {
        char[] code = source.toCharArray();
        Matcher nonCode = NON_CODE.matcher(source);
        while (nonCode.find()) {
            for (int index = nonCode.start(); index < nonCode.end(); index++) {
                if (code[index] != '\n' && code[index] != '\r')
                    code[index] = ' ';
            }
        }
        return new String(code);
    }

    private record PermissionScan(int stpCalls, int scopeCalls) {
    }

    private Map<String, Class<?>> permissionCatalogClasses() throws ClassNotFoundException {
        Map<String, Class<?>> catalogs = new HashMap<>();
        for (Class<?> candidate : scanClasses(new RegexPatternTypeFilter(CATALOG_CLASS))) {
            catalogs.put(candidate.getSimpleName(), candidate);
        }
        assertThat(catalogs).as("Permission Catalog classes under com.xsy.scm").isNotEmpty();
        return catalogs;
    }

    private Set<String> permissionCatalogValues(Iterable<Class<?>> catalogs) throws IllegalAccessException {
        Set<String> values = new HashSet<>();
        for (Class<?> catalog : catalogs) {
            for (Field field : catalog.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (field.getType() == String.class && Modifier.isPublic(modifiers)
                        && Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)) {
                    values.add((String) field.get(null));
                }
            }
        }
        return values;
    }

    private Set<Class<?>> scanClasses(org.springframework.core.type.filter.TypeFilter filter)
            throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(filter);
        Set<Class<?>> classes = new HashSet<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents(BASE_PACKAGE)) {
            String className = candidate.getBeanClassName();
            if (className != null) {
                classes.add(ClassUtils.forName(className, Thread.currentThread().getContextClassLoader()));
            }
        }
        return classes;
    }

    private static void addPermissionValues(SaCheckPermission permission, Set<String> target) {
        if (permission != null) {
            Arrays.stream(permission.value()).filter(value -> !value.isBlank()).forEach(target::add);
        }
    }
}
