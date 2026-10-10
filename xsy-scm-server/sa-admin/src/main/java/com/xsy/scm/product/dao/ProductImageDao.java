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
     * 图片中心「还没挂到商品」的公开图片。
     *
     * <p>
     * 「已绑定」的判据是 {@code product_image} 里有没有同一 fileKey 的活行，所以这条 SQL 必须同时看到文件表和商品图片表；
     * 它是只读查询，不写文件表。上传但从未绑定过的图（含按文件名批量导入后未命中的）就靠这条查出来。
     */
    List<ProductUnboundImageVO> selectUnboundPublicImages(@Param("folderType") Integer folderType,
            @Param("limit") int limit);
}
