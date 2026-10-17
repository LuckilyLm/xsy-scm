package com.xsy.scm.preference.filter;

import com.xsy.scm.preference.filter.UserFilterPresetModel.Row;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

@Mapper
public interface UserFilterPresetDao {
    @Select("""
            SELECT 1 FROM (
              SELECT pg_advisory_xact_lock(hashtext(#{ownerKey}), hashtext(#{viewKey}))
            ) locked
            """)
    int lockOwner(@Param("ownerKey") String ownerKey, @Param("viewKey") String viewKey);

    @Select("""
            SELECT count(*) FROM xsy_user_filter_preset
            WHERE owner_key = #{ownerKey} AND view_key = #{viewKey}
            """)
    int count(@Param("ownerKey") String ownerKey, @Param("viewKey") String viewKey);

    @Select("""
            SELECT id, name, conditions::text AS conditions_json, created_at
            FROM xsy_user_filter_preset
            WHERE owner_key = #{ownerKey} AND view_key = #{viewKey}
            ORDER BY id DESC LIMIT 20
            """)
    @Results({
            @Result(column = "conditions_json", property = "conditionsJson"),
            @Result(column = "created_at", property = "createdAt")
    })
    List<Row> list(@Param("ownerKey") String ownerKey, @Param("viewKey") String viewKey);

    @Insert("""
            INSERT INTO xsy_user_filter_preset(owner_key, view_key, name, conditions)
            VALUES (#{ownerKey}, #{viewKey}, #{name}, CAST(#{conditionsJson} AS jsonb))
            """)
    @Options(useGeneratedKeys = true, keyProperty = "row.id")
    int insert(@Param("ownerKey") String ownerKey, @Param("viewKey") String viewKey,
            @Param("name") String name, @Param("conditionsJson") String conditionsJson,
            @Param("row") Row row);

    @Delete("""
            DELETE FROM xsy_user_filter_preset
            WHERE id = #{id} AND owner_key = #{ownerKey} AND view_key = #{viewKey}
            """)
    int remove(@Param("id") Long id, @Param("ownerKey") String ownerKey,
            @Param("viewKey") String viewKey);
}
