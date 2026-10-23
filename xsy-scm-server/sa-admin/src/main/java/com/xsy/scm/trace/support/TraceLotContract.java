package com.xsy.scm.trace.support;

import com.xsy.scm.trace.domain.vo.TraceLotVO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 采购收货把批次事实交给追溯域的中立契约。
 *
 * <p>
 * 批次档案归追溯域所有；采购只描述「这一行分了几个批次、各多少量」，库存只消费批次身份（{@code lotId}）记子账。 库存域不反向依赖追溯域的类型，避免两个域互相引用。
 */
public interface TraceLotContract {

    /**
     * 一行收货要登记的批次：来源批号由供应商或现场提供，允许为空；数量必填。
     */
    record LotDraft(String sourceLotNo, BigDecimal quantity) {
    }

    /**
     * 一条收货行的批次登记请求；{@code operator} 取收货确认人，不用当前登录人。
     */
    record CreateReceiptLots(Long receiptItemId, Long skuId, Long supplierId, String operator, List<LotDraft> drafts) {
    }

    /**
     * 登记结果：{@code lotId} 给库存记子账，{@code lotNo} 给页面展示。
     */
    record LotAllocation(Long lotId, String lotNo, String sourceLotNo, BigDecimal quantity) {
    }

    /**
     * 在调用方事务内登记批次并回填收货行批次事实。
     *
     * <p>
     * 内部批次号由系统发号；来源批号原样保存、不参与唯一约束。数量校验由调用方先做（与收货量对账），这里只做自身合法性检查。
     */
    List<LotAllocation> createReceiptLots(CreateReceiptLots command);

    /**
     * 按收货行批量装配批次事实，供详情页展示；没有批次的行不会出现在结果里。
     */
    List<TraceLotVO.ReceiptItemLot> listByReceiptItemIds(List<Long> receiptItemIds);
}
