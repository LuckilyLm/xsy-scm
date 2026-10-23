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
 * 操作日志的前后内容用 JSONB Map 保存，支持金额和嵌套字段。放在公共模块，各业务模块直接用，不用互相依赖。
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
