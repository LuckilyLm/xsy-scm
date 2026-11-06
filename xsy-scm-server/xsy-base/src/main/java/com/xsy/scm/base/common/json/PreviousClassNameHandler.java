package com.xsy.scm.base.common.json;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import org.springframework.util.ClassUtils;

/** 读取升级前保存在缓存中的类型名称，不需要清空现有缓存或保留旧包下的Java类。 */
public class PreviousClassNameHandler extends DeserializationProblemHandler {
    private static final Map<String, String> ALIASES = aliases();

    /** 只映射已知旧包类型，未知名称交给 Jackson 原样报错。 */
    @Override
    public JavaType handleUnknownTypeId(DeserializationContext context, JavaType baseType, String previousName,
        TypeIdResolver resolver, String failureMessage) {
        String componentName = previousName;
        int dimensions = 0;
        while (dimensions < previousName.length() && previousName.charAt(dimensions) == '[') {
            dimensions++;
        }
        if (dimensions > 0) {
            if (dimensions >= previousName.length() || previousName.charAt(dimensions) != 'L'
                || !previousName.endsWith(";")) {
                return null;
            }
            componentName = previousName.substring(dimensions + 1, previousName.length() - 1);
        }
        int nested = componentName.indexOf('$');
        String outer = nested < 0 ? componentName : componentName.substring(0, nested);
        String currentName = ALIASES.get(outer);
        if (currentName == null) {
            for (var alias : ALIASES.entrySet()) {
                if (alias.getKey().endsWith(".") && outer.startsWith(alias.getKey())) {
                    currentName = alias.getValue() + outer.substring(alias.getKey().length());
                    break;
                }
            }
        }
        if (currentName == null) {
            return null;
        }
        if (nested >= 0) {
            currentName += componentName.substring(nested);
        }
        if (dimensions > 0) {
            currentName = "[".repeat(dimensions) + "L" + currentName + ";";
        }
        try {
            Class<?> type = ClassUtils.forName(currentName, PreviousClassNameHandler.class.getClassLoader());
            return baseType.getRawClass().isAssignableFrom(type) ? context.constructType(type) : null;
        } catch (ClassNotFoundException | LinkageError exception) {
            return null;
        }
    }

    private static Map<String, String> aliases() {
        try (var stream = PreviousClassNameHandler.class.getResourceAsStream("/xsy-previous-class-names.properties")) {
            if (stream == null) {
                throw new IllegalStateException("缺少升级前类型名称对应表");
            }
            Properties properties = new Properties();
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return properties.stringPropertyNames().stream()
                .collect(Collectors.toUnmodifiableMap(name -> name, properties::getProperty));
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取升级前类型名称对应表", exception);
        }
    }
}
