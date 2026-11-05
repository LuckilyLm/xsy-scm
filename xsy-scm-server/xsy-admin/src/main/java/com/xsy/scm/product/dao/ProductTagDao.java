package com.xsy.scm.product.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.product.domain.entity.ProductTagEntity;
import com.xsy.scm.product.domain.form.ProductAssistantQueryForm;
import com.xsy.scm.product.domain.vo.ProductTagVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProductTagDao extends BaseMapper<ProductTagEntity> {

    List<ProductTagVO> selectWithProductCount(@Param("query") ProductAssistantQueryForm query);

    ProductTagVO selectVoById(@Param("id") Long id);

    /**
     * 打标入口按 id 升序加行锁，与删除标签互斥；停用标签也会返回，由调用方判为不可用，历史商品仍能读出名称。
     */
    List<ProductTagEntity> lockByIds(@Param("ids") List<Long> ids);

    ProductTagEntity selectForUpdate(@Param("id") Long id);
}
