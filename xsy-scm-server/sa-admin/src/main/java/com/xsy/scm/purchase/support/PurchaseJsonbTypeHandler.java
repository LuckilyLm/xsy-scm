package com.xsy.scm.purchase.support;

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
 * 采购域 JSONB Map 类型处理器。显式使用 PG {@code jsonb}，避免 JDBC 将其按 {@code varchar} 处理。独立于订单域实现，避免建立 {@code purchase -> order}
 * 的代码依赖。
 */
public class PurchaseJsonbTypeHandler extends BaseTypeHandler<Map<String, Object>> {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public void setNonNullParameter(PreparedStatement statement, int index, Map<String, Object> jsonValue,
            JdbcType jdbcType) throws SQLException {
        try {
            PGobject pg = new PGobject();
            pg.setType("jsonb");
            pg.setValue(JSON.writeValueAsString(jsonValue));
            statement.setObject(index, pg);
        } catch (JsonProcessingException e) {
            throw new SQLException("Invalid purchase JSON", e);
        }
    }

    private Map<String, Object> read(String jsonText) throws SQLException {
        if (jsonText == null) {
            return null;
        }
        try {
            return JSON.readValue(jsonText, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            throw new SQLException("Invalid purchase JSON", e);
        }
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return read(resultSet.getString(columnName));
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return read(resultSet.getString(columnIndex));
    }

    @Override
    public Map<String, Object> getNullableResult(CallableStatement statement, int columnIndex) throws SQLException {
        return read(statement.getString(columnIndex));
    }
}
