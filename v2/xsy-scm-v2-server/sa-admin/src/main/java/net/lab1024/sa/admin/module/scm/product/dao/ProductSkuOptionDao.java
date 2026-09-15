package net.lab1024.sa.admin.module.scm.product.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import net.lab1024.sa.admin.module.scm.product.domain.form.ProductSkuOptionQueryForm;
import net.lab1024.sa.admin.module.scm.product.domain.vo.ProductSkuOptionVO;
@Mapper public interface ProductSkuOptionDao {
    List<ProductSkuOptionVO> options(@Param("query") ProductSkuOptionQueryForm query,@Param("limitPlusOne") int limitPlusOne);
    List<ProductSkuOptionVO> selectByIds(@Param("ids") List<Long> ids);
}
