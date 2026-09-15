package net.lab1024.sa.admin.module.scm.common.json;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.postgresql.util.PGobject;
import java.sql.*;
import java.util.Map;

public class JsonbStringMapTypeHandler extends BaseTypeHandler<Map<String,String>> {
    private static final ObjectMapper JSON = new ObjectMapper();
    @Override public void setNonNullParameter(PreparedStatement statement, int index, Map<String,String> value, JdbcType type) throws SQLException {
        try {
            PGobject json = new PGobject();
            json.setType("jsonb"); json.setValue(JSON.writeValueAsString(value));
            statement.setObject(index, json);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new SQLException("Invalid specification JSON", e); }
    }
    private Map<String,String> read(String value) throws SQLException {
        if (value == null) return null;
        try { return JSON.readValue(value, new TypeReference<>() {}); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new SQLException("Invalid specification JSON", e); }
    }
    @Override public Map<String,String> getNullableResult(ResultSet rs,String name) throws SQLException { return read(rs.getString(name)); }
    @Override public Map<String,String> getNullableResult(ResultSet rs,int index) throws SQLException { return read(rs.getString(index)); }
    @Override public Map<String,String> getNullableResult(CallableStatement cs,int index) throws SQLException { return read(cs.getString(index)); }
}
