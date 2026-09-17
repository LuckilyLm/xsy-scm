package net.lab1024.sa.admin.module.scm.purchase.support;

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
 * 采购域 JSONB ↔ {@code Map<String,Object>} 映射（与 W4 {@code OrderJsonbTypeHandler} 同构）。
 *
 * <p>为什么需要它：PG 的 {@code jsonb} 列经 JDBC 读出来是 {@link PGobject}，
 * MyBatis 默认拿不到 {@code Map}；写入时也需要显式包成 {@code jsonb} 类型，
 * 否则会被当成 {@code varchar} 而触发 {@code column is of type jsonb but expression is of type character varying}。
 *
 * <p>**刻意不复用 {@code OrderJsonbTypeHandler}**：跨域复用会让 {@code purchase} 依赖 {@code order} 域，
 * 而 W5 对 W4 只有「只读引用 {@code sales_order} / {@code sales_order_item}」这一条依赖方向。
 */
public class PurchaseJsonbTypeHandler extends BaseTypeHandler<Map<String, Object>> {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public void setNonNullParameter(PreparedStatement statement, int index, Map<String, Object> value, JdbcType type)
            throws SQLException {
        try {
            PGobject pg = new PGobject();
            pg.setType("jsonb");
            pg.setValue(JSON.writeValueAsString(value));
            statement.setObject(index, pg);
        } catch (JsonProcessingException e) {
            throw new SQLException("Invalid purchase JSON", e);
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
