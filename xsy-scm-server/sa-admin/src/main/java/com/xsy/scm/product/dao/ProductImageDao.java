package com.xsy.scm.product.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.product.domain.entity.ProductImageEntity;
import com.xsy.scm.product.domain.vo.ProductUnboundImageVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProductImageDao extends BaseMapper<ProductImageEntity> {
    int clearPrimary(@Param("spuId") Long spuId);

    /**
     * 查询尚未绑定商品的公开图片。已绑定的依据是商品图片表中存在有效文件键关联。
     */
    List<ProductUnboundImageVO> selectUnboundPublicImages(@Param("folderType") Integer folderType,
        @Param("limit") int limit);
}
