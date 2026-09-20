package net.lab1024.sa.admin.module.scm.product.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import net.lab1024.sa.admin.module.scm.product.domain.entity.ProductTagRelationEntity;
import net.lab1024.sa.admin.module.scm.product.domain.vo.ProductSpuTagVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ProductTagRelationDao extends BaseMapper<ProductTagRelationEntity> {

    /** 一次取多个 SPU 的标签，供列表与详情富化，避免逐行查询。 */
    List<ProductSpuTagVO> selectBySpuIds(@Param("spuIds") List<Long> spuIds);

    /** 商品当前已绑定的标签 id（含停用标签），用于区分「保留历史绑定」与「新增绑定」。 */
    List<Long> selectTagIds(@Param("spuId") Long spuId);

    /** spuIds × tagIds 的笛卡尔积打标；活动关系已存在时整行跳过（Q11：冲突目标与唯一索引逐字一致）。 */
    int insertIgnore(@Param("spuIds") List<Long> spuIds, @Param("tagIds") List<Long> tagIds, @Param("operator") String operator);

    /** REPLACE 语义：把这些 SPU 上不在 tagIds 内的活动关系下线；tagIds 为空表示清空。 */
    int softDeleteExcept(@Param("spuIds") List<Long> spuIds, @Param("tagIds") List<Long> tagIds, @Param("operator") String operator);

    int softDelete(@Param("spuIds") List<Long> spuIds, @Param("tagIds") List<Long> tagIds, @Param("operator") String operator);

    int softDeleteBySpuIds(@Param("spuIds") List<Long> spuIds, @Param("operator") String operator);
}
