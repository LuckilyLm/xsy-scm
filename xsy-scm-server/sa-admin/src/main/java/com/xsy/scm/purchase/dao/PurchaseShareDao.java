package com.xsy.scm.purchase.dao;

import com.xsy.scm.purchase.domain.vo.PurchaseSharePublicVO;
import com.xsy.scm.purchase.domain.vo.PurchaseShareVO;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PurchaseShareDao {

    /** 撤销该采购单上仍有效的链接：重新分享时旧码立即失效。 */
    int revokeActiveByOrder(@Param("purchaseOrderId") Long purchaseOrderId,
            @Param("revokedAt") OffsetDateTime revokedAt);

    int insertToken(PurchaseShareVO token);

    PurchaseShareVO findByHash(@Param("tokenHash") String tokenHash);

    PurchaseShareVO findById(@Param("id") Long id);

    List<PurchaseShareVO> listByOrder(@Param("purchaseOrderId") Long purchaseOrderId);

    int touchUsed(@Param("id") Long id, @Param("usedAt") OffsetDateTime usedAt);

    int insertFeedback(@Param("shareTokenId") Long shareTokenId, @Param("purchaseOrderId") Long purchaseOrderId,
            @Param("purchaseOrderItemId") Long purchaseOrderItemId, @Param("unitPrice") BigDecimal unitPrice,
            @Param("receivedQuantity") BigDecimal receivedQuantity, @Param("remark") String remark,
            @Param("operatorIp") String operatorIp, @Param("createdBy") String createdBy);

    PurchaseSharePublicVO header(@Param("purchaseOrderId") Long purchaseOrderId);

    List<PurchaseSharePublicVO.Line> lines(@Param("purchaseOrderId") Long purchaseOrderId);

    List<Long> itemIdsOfOrder(@Param("purchaseOrderId") Long purchaseOrderId);
}
