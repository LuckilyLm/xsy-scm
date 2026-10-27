package com.xsy.scm.print.service;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.order.dao.SalesOrderDao;
import com.xsy.scm.order.domain.entity.SalesOrderEntity;
import com.xsy.scm.print.constant.ScmPrintDocumentTypeEnum;
import com.xsy.scm.print.constant.ScmPrintErrorCode;
import com.xsy.scm.print.domain.form.ScmPrintActionForm;
import com.xsy.scm.print.domain.form.ScmPrintBatchForm;
import com.xsy.scm.print.domain.vo.ScmPrintBatchItemVO;
import com.xsy.scm.print.domain.vo.ScmPrintBatchResultVO;
import com.xsy.scm.print.domain.vo.ScmPrintRenderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 20:00 的客户分拣单/送货单批量预览与打印。
 *
 * <p>
 * 每张订单单独调用 {@link ScmPrintService}，因此每单有自己的事务、数据范围校验和幂等记录： 一张失败不会回滚其他单，失败项可在结果里单独重试。批量本身不重新实现渲染或模板解析。
 */
@Service
@RequiredArgsConstructor
public class ScmPrintBatchService {
    private static final int MAX_ORDERS = 200;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final SalesOrderDao salesOrderDao;
    private final ScmDataScopeService dataScopeService;
    private final ScmPrintService scmPrintService;

    public ScmPrintBatchResultVO preview(ScmPrintBatchForm form) {
        return run(form, false);
    }

    public ScmPrintBatchResultVO print(ScmPrintBatchForm form) {
        return run(form, true);
    }

    private ScmPrintBatchResultVO run(ScmPrintBatchForm form, boolean print) {
        ScmPrintDocumentTypeEnum type = requireBatchType(form.getDocumentType());
        List<Long> orderIds = resolveOrderIds(form);
        if (orderIds.size() > MAX_ORDERS) {
            throw new ScmBusinessException(ScmPrintErrorCode.BATCH_LIMIT_EXCEEDED);
        }
        var result = new ScmPrintBatchResultVO();
        result.setTotal(orderIds.size());
        var action = new ScmPrintActionForm();
        action.setTemplateId(form.getTemplateId());
        String batchKey = form.getBatchKey() == null || form.getBatchKey().isBlank()
            ? UUID.randomUUID().toString()
            : form.getBatchKey();
        for (Long orderId : orderIds) {
            var item = new ScmPrintBatchItemVO();
            item.setOrderId(orderId);
            try {
                ScmPrintRenderVO render = print
                    ? scmPrintService.print(type.name(), orderId, action, batchKey + ":" + orderId)
                    : scmPrintService.preview(type.name(), orderId, form.getTemplateId());
                item.setSuccess(true);
                item.setOrderNo(render.getBusinessNo());
                item.setRecordId(render.getRecordId());
                item.setCustomerName(headerValue(render, "customerName"));
                result.getRenders().add(render);
                result.setSucceeded(result.getSucceeded() + 1);
            } catch (ScmBusinessException exception) {
                item.setSuccess(false);
                item.setMessage(exception.getErrorCode().getMsg());
                result.setFailed(result.getFailed() + 1);
            } catch (RuntimeException exception) {
                // 单张失败不扩大成整批 500；具体原因留给服务端日志，纸面只提示可重试
                item.setSuccess(false);
                item.setMessage("该订单打印失败，请重试或单独打印");
                result.setFailed(result.getFailed() + 1);
            }
            result.getItems().add(item);
        }
        return result;
    }

    /**
     * 指定订单时按 id 打印；否则按配送日期取已确认订单。两种路径都要先过订单业务员范围， 指定 id 的范围由逐单 {@link ScmPrintService} 的可见性校验兜底。
     */
    private List<Long> resolveOrderIds(ScmPrintBatchForm form) {
        if (form.getOrderIds() != null && !form.getOrderIds().isEmpty()) {
            return form.getOrderIds().stream().filter(Objects::nonNull).distinct().toList();
        }
        ScmDataScopeContext context = dataScopeService.resolve();
        if (context.getOrderSellerScope().isEmpty()) {
            return List.of();
        }
        LocalDate date = form.getBusinessDate();
        var start = date.atStartOfDay(ZONE).toOffsetDateTime();
        var end = date.plusDays(1).atStartOfDay(ZONE).toOffsetDateTime();
        return salesOrderDao
            .selectForCustomerSheet(start, end, context.getOrderSellerScope(), form.getRouteId(), form.getCustomerIds())
            .stream().map(SalesOrderEntity::getId).toList();
    }

    private static String headerValue(ScmPrintRenderVO render, String key) {
        if (render.getHeaderFields() == null) {
            return null;
        }
        return render.getHeaderFields().stream().filter(field -> key.equals(field.getKey()))
            .map(field -> field.getValue()).findFirst().orElse(null);
    }

    private static ScmPrintDocumentTypeEnum requireBatchType(String documentType) {
        if (!ScmPrintDocumentTypeEnum.isSupported(documentType)) {
            throw new ScmBusinessException(ScmPrintErrorCode.BATCH_TYPE_UNSUPPORTED);
        }
        ScmPrintDocumentTypeEnum type = ScmPrintDocumentTypeEnum.valueOf(documentType);
        if (type != ScmPrintDocumentTypeEnum.CUSTOMER_SORTING_SHEET
            && type != ScmPrintDocumentTypeEnum.CUSTOMER_DELIVERY_NOTE) {
            throw new ScmBusinessException(ScmPrintErrorCode.BATCH_TYPE_UNSUPPORTED);
        }
        return type;
    }
}
