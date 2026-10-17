package com.xsy.scm.order.dao;

import com.xsy.scm.order.domain.vo.OrderTagVO;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;

@Mapper
public interface OrderTagDao {
    @Select("SELECT NULL::BIGINT AS order_id, id AS tag_id, name, color "
            + "FROM xsy_order_tag WHERE enabled = TRUE ORDER BY id")
    @Results(id = "orderTag", value = {
            @Result(column = "order_id", property = "orderId"),
            @Result(column = "tag_id", property = "tagId"),
            @Result(column = "name", property = "name"),
            @Result(column = "color", property = "color")
    })
    List<OrderTagVO> tags();

    @Select("SELECT NULL::BIGINT AS order_id, id AS tag_id, name, color "
            + "FROM xsy_order_tag WHERE id = #{tagId} AND enabled = TRUE")
    @ResultMap("orderTag")
    OrderTagVO tag(@Param("tagId") Long tagId);

    @Insert("""
            INSERT INTO xsy_order_tag(name, color, created_by)
            VALUES (#{tag.name}, #{tag.color}, #{operator})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "tag.tagId")
    int create(@Param("tag") OrderTagVO tag, @Param("operator") String operator);

    @Select("""
            <script>
            SELECT l.order_id, t.id AS tag_id, t.name, t.color
            FROM xsy_order_tag_link l JOIN xsy_order_tag t ON t.id = l.tag_id
            WHERE l.order_id IN
            <foreach collection="orderIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            ORDER BY t.id
            </script>
            """)
    @ResultMap("orderTag")
    List<OrderTagVO> tagsByOrders(@Param("orderIds") List<Long> orderIds);

    @Insert("""
            INSERT INTO xsy_order_tag_link(order_id, tag_id, created_by)
            VALUES (#{orderId}, #{tagId}, #{operator}) ON CONFLICT (order_id, tag_id) DO NOTHING
            """)
    int attach(@Param("orderId") Long orderId, @Param("tagId") Long tagId,
            @Param("operator") String operator);

    @Delete("DELETE FROM xsy_order_tag_link WHERE order_id = #{orderId} AND tag_id = #{tagId}")
    int detach(@Param("orderId") Long orderId, @Param("tagId") Long tagId);
}
