package com.xsy.scm.order.support;

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

public class OrderJsonbTypeHandler extends BaseTypeHandler<Map<String, Object>> {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public void setNonNullParameter(PreparedStatement preparedStatement, int parameterIndex, Map<String,
        Object> jsonValue, JdbcType jdbcType) throws SQLException {
        try {
            var postgresJsonObject = new PGobject();
            postgresJsonObject.setType("jsonb");
            postgresJsonObject.setValue(JSON.writeValueAsString(jsonValue));
            preparedStatement.setObject(parameterIndex, postgresJsonObject);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new SQLException("Invalid order JSON", e);
        }
    }

    private Map<String, Object> read(String jsonValue) throws SQLException {
        if (jsonValue == null) return null;
        try {
            return JSON.readValue(jsonValue, new TypeReference<>() {
            });
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new SQLException("Invalid order JSON", e);
        }
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet resultSet, String columnLabel) throws SQLException {
        return read(resultSet.getString(columnLabel));
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return read(resultSet.getString(columnIndex));
    }

    @Override
    public Map<String, Object> getNullableResult(CallableStatement callableStatement,
        int parameterIndex) throws SQLException {
        return read(callableStatement.getString(parameterIndex));
    }
}
