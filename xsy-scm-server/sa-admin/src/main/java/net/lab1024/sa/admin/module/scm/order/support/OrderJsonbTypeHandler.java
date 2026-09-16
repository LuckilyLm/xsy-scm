package net.lab1024.sa.admin.module.scm.order.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.*;
import org.postgresql.util.PGobject;
import java.sql.*;
import java.util.Map;
public class OrderJsonbTypeHandler extends BaseTypeHandler<Map<String,Object>> {
    private static final ObjectMapper JSON=new ObjectMapper();
    @Override public void setNonNullParameter(PreparedStatement s,int i,Map<String,Object> value,JdbcType type) throws SQLException {
        try { var p=new PGobject();p.setType("jsonb");p.setValue(JSON.writeValueAsString(value));s.setObject(i,p); }
        catch(com.fasterxml.jackson.core.JsonProcessingException e) { throw new SQLException("Invalid order JSON",e); }
    }
    private Map<String,Object> read(String value) throws SQLException {
        if(value==null) return null;
        try { return JSON.readValue(value,new TypeReference<>(){}); }
        catch(com.fasterxml.jackson.core.JsonProcessingException e) { throw new SQLException("Invalid order JSON",e); }
    }
    @Override public Map<String,Object> getNullableResult(ResultSet r,String n) throws SQLException {return read(r.getString(n));}
    @Override public Map<String,Object> getNullableResult(ResultSet r,int n) throws SQLException {return read(r.getString(n));}
    @Override public Map<String,Object> getNullableResult(CallableStatement r,int n) throws SQLException {return read(r.getString(n));}
}
