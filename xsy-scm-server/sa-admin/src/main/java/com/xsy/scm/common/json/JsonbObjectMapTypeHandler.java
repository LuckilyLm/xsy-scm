package com.xsy.scm.common.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

/**
 * JSONB ↔ {@code Map<String, Object>} 的共享 TypeHandler，用于操作日志的 before / after 快照
 * （快照里有金额、数量与嵌套对象，{@code Map<String, String>} 表达不了）。
 *
 * <p>
 * {@code order} 域有一份等价的 {@code OrderJsonbTypeHandler}，未合并是为了不让 {@code finance} 依赖 {@code order/support}；合并是一次纯 Java
 * 重构，不涉及 migration、不改变对外行为，留待后续统一处理。
 */
public class JsonbObjectMapTypeHandler extends BaseTypeHandler<Map<String, Object>> {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public void setNonNullParameter(PreparedStatement statement, int index, Map<String, Object> value, JdbcType type)
            throws SQLException {
        try {
            PGobject json = new PGobject();
            json.setType("jsonb");
            json.setValue(JSON.writeValueAsString(value));
            statement.setObject(index, json);
        } catch (JsonProcessingException e) {
            throw new SQLException("Invalid JSONB object map", e);
        }
    }

    private Map<String, Object> read(String value) throws SQLException {
        if (value == null) {
            return null;
        }
        try {
            return JSON.readValue(value, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            throw new SQLException("Invalid JSONB object map", e);
        }
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet rs, String name) throws SQLException {
        return read(rs.getString(name));
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet rs, int index) throws SQLException {
        return read(rs.getString(index));
    }

    @Override
    public Map<String, Object> getNullableResult(CallableStatement cs, int index) throws SQLException {
        return read(cs.getString(index));
    }
}
