package net.lab1024.sa.admin.module.scm.common.json;

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
 * JSONB ↔ {@code Map<String, Object>} 的共享 TypeHandler，用于操作日志的 before / after 快照。
 *
 * <p>与同包的 {@link JsonbStringMapTypeHandler} 只差值类型：快照里会出现金额、数量与嵌套对象，
 * {@code Map<String, String>} 表达不了。
 *
 * <p>{@code order} 域已有一份等价的 {@code OrderJsonbTypeHandler}。这里不直接复用它，
 * 是为了不让 {@code finance} 依赖 {@code order/support}；把两份合并到本包是一次纯 Java 重构，
 * 不涉及 migration、不改变对外行为，留待后续统一处理（与 {@code ScmCommonErrorCode} 里
 * 记录的 40921 重复声明同一处置取向：先记录为已知技术债，不顺手重构）。
 */
public class JsonbObjectMapTypeHandler extends BaseTypeHandler<Map<String, Object>> {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public void setNonNullParameter(PreparedStatement statement, int index, Map<String, Object> value,
                                    JdbcType type) throws SQLException {
        try {
            PGobject json = new PGobject();
            json.setType("jsonb");
            json.setValue(JSON.writeValueAsString(value));
            statement.setObject(index, json);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
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
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
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
