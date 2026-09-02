package com.xianshuyuan.scm.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xianshuyuan.scm.product.entity.ProductCategoryEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProductCategoryMapper extends BaseMapper<ProductCategoryEntity> {

    @Select("""
        SELECT * FROM product_category
        WHERE deleted = FALSE
        ORDER BY sort_order, id
        """)
    List<ProductCategoryEntity> selectActiveCategories();

    @Select("""
        SELECT COUNT(*) FROM product_category
        WHERE parent_id = #{parentId} AND deleted = FALSE
        """)
    long countActiveChildren(long parentId);

    @Select("""
        SELECT COUNT(*) FROM product_spu
        WHERE category_id = #{categoryId} AND deleted = FALSE
        """)
    long countActiveProducts(long categoryId);
}
