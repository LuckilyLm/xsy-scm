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
            "(?:[A-Za-z_$][\\w$]*\\.)*([A-Za-z_$][\\w$]*Permission)\\.([A-Z][A-Z0-9_]*)");
    private static final Pattern STP_CHECK = Pattern.compile(
            "\\bStpUtil\\s*\\.\\s*checkPermission\\s*\\(([^)]*)\\)");

    @Test
    @DisplayName("Controller 与 StpUtil 权限均来自目录并已发布到 t_menu.api_perms")
    void usedPermissionsAreCataloguedAndPublished() throws Exception {
        Map<String, Class<?>> catalogs = permissionCatalogClasses();
        Set<String> catalogValues = permissionCatalogValues(catalogs.values());
        Set<String> usedValues = new HashSet<>();
        int endpointCount = addControllerPermissions(usedValues);
        int stpCallCount = addStpPermissionChecks(catalogs, usedValues);

        assertThat(endpointCount).as("SCM Controller endpoint scanner 必须命中实际端点").isPositive();
        assertThat(stpCallCount).as("SCM StpUtil permission scanner 必须命中直接权限检查").isPositive();
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

    private int addStpPermissionChecks(Map<String, Class<?>> catalogs, Set<String> usedValues) throws Exception {
        Path classesDirectory = Path.of(AdminApplication.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path sourceRoot = classesDirectory.getParent().getParent().resolve("src/main/java/com/xsy/scm");
        assertThat(sourceRoot).as("SCM source tree for direct permission call sites").isDirectory();

        int callCount = 0;
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            for (Path sourceFile : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(sourceFile, StandardCharsets.UTF_8);
                Matcher checks = STP_CHECK.matcher(source);
                while (checks.find()) {
                    callCount++;
                    for (String argument : checks.group(1).split(",")) {
                        Matcher fieldReference = PERMISSION_FIELD.matcher(argument.trim());
                        assertThat(fieldReference.matches())
                                .as("StpUtil.checkPermission must receive a Permission Catalog field in %s", sourceFile)
                                .isTrue();
                        Class<?> catalog = catalogs.get(fieldReference.group(1));
                        assertThat(catalog)
                                .as("StpUtil catalog class %s must be discovered", fieldReference.group(1))
                                .isNotNull();
                        Field field = catalog.getField(fieldReference.group(2));
                        assertThat(field.getType()).isEqualTo(String.class);
                        assertThat(Modifier.isStatic(field.getModifiers())).isTrue();
                        usedValues.add((String) field.get(null));
                    }
                }
            }
        }
        return callCount;
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
