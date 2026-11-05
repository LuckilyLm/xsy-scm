package com.xsy.scm.common.json;

import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Map;

/**
 * 操作日志、保存的数据等 JSONB Map 字段，支持金额和嵌套对象。
 */
public class JsonbObjectMapTypeHandler extends AbstractJsonbTypeHandler<Map<String, Object>> {

    public JsonbObjectMapTypeHandler() {
        super(new TypeReference<>() {
        });
    }

    @Override
    protected String invalidJsonMessage() {
        return "Invalid JSONB object map";
    }
}
