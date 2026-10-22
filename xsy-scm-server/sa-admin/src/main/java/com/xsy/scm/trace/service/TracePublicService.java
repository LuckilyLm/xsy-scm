package com.xsy.scm.trace.service;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.trace.constant.TraceErrorCode;
import com.xsy.scm.trace.dao.TracePublicQueryDao;
import com.xsy.scm.trace.dao.TraceUnitDao;
import com.xsy.scm.trace.domain.vo.TracePublicVO;
import com.xsy.scm.trace.domain.vo.TraceUnitVO;
import com.xsy.scm.trace.support.TracePublicRateLimiter;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.file.constant.FileRelationBizTypeEnum;
import net.lab1024.sa.base.module.support.file.domain.vo.FileDownloadVO;
import net.lab1024.sa.base.module.support.file.service.IFileStorageService;
import org.springframework.stereotype.Service;

/**
 * 免登录扫码页的读路径。
 *
 * <p>
 * 与后台回放的区别有三处，都是刻意的：
 * <ul>
 * <li>凭据是标签上的 {@code public_token}，不是员工登录态；因此不做仓库数据范围校验——能扫到这个码就是访问凭据。</li>
 * <li>返回字段经过裁剪，客户名与订单号脱敏，不返回内部主键、操作人与批次级出库明细。</li>
 * <li>每次都写一条审计并受本地限流约束，用来发现按令牌枚举的爬取。</li>
 * </ul>
 *
 * <p>
 * 这里不加 {@code @Transactional}：几条读语句各自成事务，换来的是「审计写入失败不会把整次查询拖成 500」。 公开页对一致性没有强要求，一次刷新看到的状态差几毫秒可以接受。
 */
@Service
@RequiredArgsConstructor
public class TracePublicService {

    private final TraceUnitDao traceUnitDao;

    private final TracePublicQueryDao tracePublicQueryDao;

    private final TraceAttachmentService traceAttachmentService;

    private final TracePublicRateLimiter tracePublicRateLimiter;

    private final IFileStorageService fileStorageService;

    public TracePublicVO query(String publicToken, String clientIp, String userAgent) {
        String token = normalizeToken(publicToken);
        tracePublicRateLimiter.check(clientIp, token);
        TraceUnitVO.Unit unit = traceUnitDao.findByPublicToken(token);
        if (unit == null) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_PUBLIC_TOKEN_NOT_FOUND);
        }
        TraceUnitVO.Lot lot = traceUnitDao.findLot(unit.getLotId());
        TraceUnitVO.Receipt receipt = traceUnitDao.receiptOfLot(unit.getLotId());
        TraceUnitVO.Binding binding = traceUnitDao.findBinding(unit.getId());

        TracePublicVO result = new TracePublicVO();
        result.setUnitCode(unit.getUnitCode());
        result.setStatus(unit.getStatus());
        result.setPackageType(unit.getPackageType());
        result.setQuantity(unit.getQuantity());
        result.setUnit(unit.getUnit());
        result.setNetWeight(unit.getNetWeight());
        result.setWeightUnit(unit.getWeightUnit());
        result.setMeasuredAt(unit.getMeasuredAt());
        if (lot != null) {
            result.setProductName(lot.getSpuName());
            result.setSkuName(lot.getSkuName());
            result.setLotNo(lot.getLotNo());
            result.setSourceLotNo(lot.getSourceLotNo());
        }
        if (receipt != null) {
            result.setSupplierName(receipt.getSupplierName());
            result.setReceivedAt(receipt.getConfirmedAt());
        }
        if (binding != null) {
            result.setCustomerName(maskName(binding.getCustomerName()));
            result.setOrderNo(maskOrderNo(binding.getOrderNo()));
        }
        result.setAttachments(
                traceAttachmentService.publicAttachments(unit.getLotId(), lot == null ? null : lot.getSpuId()));
        audit(unit, token, clientIp, userAgent);
        return result;
    }

    /**
     * 附件下载：令牌只能取到「挂在这个码所属批次或商品上」的文件。
     *
     * <p>
     * 不能把 fileKey 直接交给通用下载接口：那一侧按员工身份判权，匿名请求一律拒绝；也不能让它 302 到静态目录， 那样等于绕过鉴权。所以归属判定在这里做，字节由存储层直接读出来。
     */
    public FileDownloadVO file(String publicToken, String fileKey, String clientIp) {
        String token = normalizeToken(publicToken);
        tracePublicRateLimiter.check(clientIp, token);
        TraceUnitVO.Unit unit = traceUnitDao.findByPublicToken(token);
        if (unit == null) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_PUBLIC_TOKEN_NOT_FOUND);
        }
        TraceUnitVO.Lot lot = traceUnitDao.findLot(unit.getLotId());
        Set<String> allowed = new HashSet<>(
                traceAttachmentService.attachmentKeysOf(FileRelationBizTypeEnum.LOT_INSPECTION, unit.getLotId()));
        if (lot != null && lot.getSpuId() != null) {
            allowed.addAll(traceAttachmentService.attachmentKeysOf(FileRelationBizTypeEnum.PRODUCT_QUALIFICATION,
                    lot.getSpuId()));
        }
        String key = fileKey == null ? "" : fileKey.trim();
        if (key.isEmpty() || !allowed.contains(key)) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_PUBLIC_FILE_NOT_ALLOWED);
        }
        ResponseDTO<FileDownloadVO> download = fileStorageService.download(key);
        if (!download.getOk() || download.getData() == null) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_PUBLIC_FILE_NOT_ALLOWED);
        }
        return download.getData();
    }

    private static String normalizeToken(String publicToken) {
        String token = publicToken == null ? "" : publicToken.trim();
        if (token.isEmpty()) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_PUBLIC_TOKEN_NOT_FOUND);
        }
        return token;
    }

    /** 审计写入失败只丢审计，不丢这次查询：扫码的人不该因为一条日志写不进去而看到错误页。 */
    private void audit(TraceUnitVO.Unit unit, String token, String clientIp, String userAgent) {
        try {
            tracePublicQueryDao.insert(unit.getId(), token, truncate(clientIp, 64), truncate(userAgent, 200),
                    OffsetDateTime.now());
        } catch (RuntimeException ignored) {
            // 审计是旁路：这里刻意不记录日志，避免被高频枚举刷爆日志文件
        }
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    /** 客户名只留姓氏：扫码的人只需要确认「这是我这单的货」，不需要看到别人的全名。 */
    private static String maskName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String trimmed = name.trim();
        return trimmed.length() <= 1 ? trimmed : trimmed.charAt(0) + "**";
    }

    /** 订单号保留首尾各 4 位：够客户对照自己的订单，又不足以被拿来猜别的单号。 */
    private static String maskOrderNo(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            return null;
        }
        String trimmed = orderNo.trim();
        if (trimmed.length() <= 8) {
            return trimmed.charAt(0) + "****";
        }
        return trimmed.substring(0, 4) + "****" + trimmed.substring(trimmed.length() - 4);
    }
}
