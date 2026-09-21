package net.lab1024.sa.admin.module.scm.product.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import net.lab1024.sa.admin.module.scm.product.domain.entity.ProductTagEntity;
import net.lab1024.sa.admin.module.scm.product.domain.form.ProductAssistantQueryForm;
import net.lab1024.sa.admin.module.scm.product.domain.vo.ProductTagVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProductTagDao extends BaseMapper<ProductTagEntity> {

    List<ProductTagVO> selectWithProductCount(@Param("query") ProductAssistantQueryForm query);

    ProductTagVO selectVoById(@Param("id") Long id);

    /**
     * 打标入口按 id 升序加行锁，与删除标签互斥；停用的标签会被返回但由调用方判为不可用，
     * 这样历史商品保留旧标签时仍能读出名称，只是不能再新挂。
     */
    List<ProductTagEntity> lockByIds(@Param("ids") List<Long> ids);

    ProductTagEntity selectForUpdate(@Param("id") Long id);
}
