package net.lab1024.sa.admin.module.scm.purchase.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.ReceiptWeighingRecordEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 称重记录（W5 Target Design §5.9，**只追加**；Q15：无 `version` / `deleted`；Q16：无 `scale_precision`）。
 *
 * <p>**刻意只有 insert 与 select**：审计事实不可修改、不可删除。
 * 缺少 update / delete 方法本身就是「只追加」这一不变量的实现
 * （与 {@code purchase_operation_log} 同一纪律）。
 */
@Mapper
public interface ReceiptWeighingRecordDao extends BaseMapper<ReceiptWeighingRecordEntity> {

    /**
     * 追加一条称重事实。
     */
    int append(@Param("row") ReceiptWeighingRecordEntity row);

    /**
     * 某收货行的全部称重记录，按时间倒序。
     */
    List<ReceiptWeighingRecordEntity> listByReceiptItemId(@Param("purchaseReceiptItemId") Long purchaseReceiptItemId);

    /**
     * 某收货单（跨行）的全部称重记录，按时间倒序。
     */
    List<ReceiptWeighingRecordEntity> listByReceiptId(@Param("purchaseReceiptId") Long purchaseReceiptId);

    /**
     * 某收货行的称重记录条数（「非标品确认必有一条称重事实」的断言用）。
     */
    int countByReceiptItemId(@Param("purchaseReceiptItemId") Long purchaseReceiptItemId);
}
