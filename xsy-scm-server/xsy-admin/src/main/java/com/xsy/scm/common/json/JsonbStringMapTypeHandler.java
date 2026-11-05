package com.xsy.scm.common.json;

import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Map;

/**
 * 商品规格等 JSONB Map&lt;String, String&gt; 字段。
 */
public class JsonbStringMapTypeHandler extends AbstractJsonbTypeHandler<Map<String, String>> {

    public JsonbStringMapTypeHandler() {
        super(new TypeReference<>() {
        });
    }

    @Override
    protected String invalidJsonMessage() {
        return "商品规格 JSON 无效";
    }
}
