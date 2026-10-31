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

/**
 * JSONB 类型处理器公共实现：显式写入 PG {@code jsonb}，避免 JDBC 按 {@code varchar} 处理。
 *
 * <p>
 * 子类只声明目标类型与错误文案；反序列化失败统一转成 {@link SQLException}，由事务回滚。
 */
abstract class AbstractJsonbTypeHandler<T> extends BaseTypeHandler<T> {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final TypeReference<T> type;

    protected AbstractJsonbTypeHandler(TypeReference<T> type) {
        this.type = type;
    }

    protected abstract String invalidJsonMessage();

    @Override
    public void setNonNullParameter(PreparedStatement statement, int index, T value, JdbcType jdbcType)
        throws SQLException {
        try {
            PGobject json = new PGobject();
            json.setType("jsonb");
            json.setValue(JSON.writeValueAsString(value));
            statement.setObject(index, json);
        } catch (JsonProcessingException e) {
            throw new SQLException(invalidJsonMessage(), e);
        }
    }

    private T read(String value) throws SQLException {
        if (value == null) {
            return null;
        }
        try {
            return JSON.readValue(value, type);
        } catch (JsonProcessingException e) {
            throw new SQLException(invalidJsonMessage(), e);
        }
    }

    @Override
    public T getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return read(rs.getString(columnName));
    }

    @Override
    public T getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return read(rs.getString(columnIndex));
    }

    @Override
    public T getNullableResult(CallableStatement statement, int columnIndex) throws SQLException {
        return read(statement.getString(columnIndex));
    }
}
