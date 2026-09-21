package net.lab1024.sa.admin.module.scm.product.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import net.lab1024.sa.admin.module.scm.product.domain.entity.ProductUomEntity;
import net.lab1024.sa.admin.module.scm.product.domain.form.ProductAssistantQueryForm;
import net.lab1024.sa.admin.module.scm.product.domain.vo.ProductUomVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProductUomDao extends BaseMapper<ProductUomEntity> {

    List<ProductUomVO> selectWithReference(@Param("query") ProductAssistantQueryForm query);

    ProductUomVO selectVoById(@Param("id") Long id);

    /**
     * 锁定活动行，使「删除单位」与「商品改用该单位」串行，避免校验通过后被并发写引用。
     */
    ProductUomEntity selectForUpdate(@Param("id") Long id);

    /**
     * 按单位名称加行锁，只返回字典里存在的活动行。
     * 名称不在字典里时不产生锁，调用方按「未维护」处理。
     */
    List<ProductUomEntity> selectNamesForUpdate(@Param("names") List<String> names);
}
