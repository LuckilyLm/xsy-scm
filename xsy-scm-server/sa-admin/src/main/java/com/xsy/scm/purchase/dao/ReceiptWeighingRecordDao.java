package com.xsy.scm.purchase.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.purchase.domain.entity.ReceiptWeighingRecordEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 称重记录，只追加：没有 {@code version} / {@code deleted}，也没有 {@code scale_precision}。
 *
 * <p>
 * 刻意只有 insert 与 select：审计记录不可修改、不可删除，缺少 update / delete 方法本身就是「只追加」的实现 （与 {@code xsy_purchase_operation_log} 同一规则）。
 */
@Mapper
public interface ReceiptWeighingRecordDao extends BaseMapper<ReceiptWeighingRecordEntity> {

    /**
     * 追加一条称重记录。
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
     * 某收货行的称重记录条数（校验「非标品确认必有一条称重记录」）。
     */
    int countByReceiptItemId(@Param("purchaseReceiptItemId") Long purchaseReceiptItemId);
}
