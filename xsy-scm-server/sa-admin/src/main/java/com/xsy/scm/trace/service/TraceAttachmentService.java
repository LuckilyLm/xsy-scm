package com.xsy.scm.trace.service;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmWarehouseScopeGuard;
import com.xsy.scm.trace.constant.TraceAttachmentCategoryEnum;
import com.xsy.scm.trace.constant.TraceErrorCode;
import com.xsy.scm.trace.dao.TraceAttachmentDao;
import com.xsy.scm.trace.dao.TraceUnitDao;
import com.xsy.scm.trace.domain.vo.TraceAttachmentVO;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import net.lab1024.sa.base.module.support.file.constant.FileRelationBizTypeEnum;
import net.lab1024.sa.base.module.support.file.dao.FileRelationDao;
import net.lab1024.sa.base.module.support.file.domain.vo.FileVO;
import net.lab1024.sa.base.module.support.file.service.FileAccessGuard;
import net.lab1024.sa.base.module.support.file.service.FileRelationService;
import net.lab1024.sa.base.module.support.file.service.FileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 溯源附件：商品资质与批次检测报告。
 *
 * <p>
 * 关系行由通用文件层维护，这里只做两件事：把对象侧的存在性/数据范围校验补上， 以及把「关系行 + 文件元数据」拼成页面要的列表。不返回 URL——URL 由下载入口按调用者身份现算。
 *
 * <p>
 * 两个入口的授权口径不同：商品资质沿用商品查看权（能看商品就能看它的资质），批次检测报告沿用包装码查看权并叠加仓库数据范围。 管理动作统一收在一枚 {@code scm:trace:file:manage}
 * 上，避免给仓库岗顺带打开商品写入口。
 */
@Service
@RequiredArgsConstructor
public class TraceAttachmentService {

    private static final int MAX_ATTACHMENTS = 20;

    private final FileRelationDao fileRelationDao;

    private final FileRelationService fileRelationService;

    private final FileService fileService;

    private final FileAccessGuard fileAccessGuard;

    private final TraceAttachmentDao traceAttachmentDao;

    private final TraceUnitDao traceUnitDao;

    private final ScmWarehouseScopeGuard warehouseScopeGuard;

    @Transactional(readOnly = true)
    public List<TraceAttachmentVO> productAttachments(Long spuId) {
        return attachmentsOf(FileRelationBizTypeEnum.PRODUCT_QUALIFICATION, spuId,
                TraceAttachmentCategoryEnum.QUALIFICATION);
    }

    @Transactional(readOnly = true)
    public List<TraceAttachmentVO> lotAttachments(Long lotId) {
        warehouseScopeGuard.require(traceUnitDao.lotWarehouseId(lotId));
        return attachmentsOf(FileRelationBizTypeEnum.LOT_INSPECTION, lotId, TraceAttachmentCategoryEnum.INSPECTION);
    }

    /** 同步商品资质：列表里没有的旧附件只撤销授权，不删除物理文件。 */
    @Transactional(rollbackFor = Exception.class)
    public void syncProductAttachments(Long spuId, Collection<String> fileKeys) {
        if (traceAttachmentDao.countLiveSpu(spuId) == 0) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_PRODUCT_NOT_FOUND);
        }
        fileRelationService.rebind(FileRelationBizTypeEnum.PRODUCT_QUALIFICATION, spuId, requireExisting(fileKeys));
    }

    @Transactional(rollbackFor = Exception.class)
    public void syncLotAttachments(Long lotId, Collection<String> fileKeys) {
        if (traceAttachmentDao.countLot(lotId) == 0) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_LOT_NOT_FOUND);
        }
        warehouseScopeGuard.require(traceUnitDao.lotWarehouseId(lotId));
        fileRelationService.rebind(FileRelationBizTypeEnum.LOT_INSPECTION, lotId, requireExisting(fileKeys));
    }

    /**
     * 去空白、去重并确认每个 key 都在文件表里存在。
     *
     * <p>
     * 不存在的 key 会写出一条谁也读不到的关系行：数据库挡不住，读侧只会静默丢掉它， 事后没人能解释「为什么这个附件不显示」。所以在写入前直接拒绝。
     */
    private List<String> requireExisting(Collection<String> fileKeys) {
        if (fileKeys == null || fileKeys.isEmpty()) {
            return List.of();
        }
        List<String> keys = fileKeys.stream().filter(key -> key != null && !key.isBlank()).map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new)).stream().limit(MAX_ATTACHMENTS).toList();
        if (keys.isEmpty()) {
            return List.of();
        }
        if (fileService.getFileMetadata(keys).size() != keys.size()) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_ATTACHMENT_NOT_FOUND);
        }
        // 新关系会授予附件读取权，必须先以原有权限校验，不能靠刚建立的关系自我授权。
        fileAccessGuard.checkRead(String.join(",", keys), SmartRequestUtil.getRequestUser());
        return keys;
    }

    /**
     * 商品资质的公开读取；批次旧附件不作为已发布检测报告。
     *
     * <p>
     * 公开页以包装令牌限定商品与批次；报告附件另由发布状态控制，后台入口仍校验仓库范围。
     */
    List<TraceAttachmentVO> publicAttachments(Long lotId, Long spuId) {
        List<TraceAttachmentVO> result = new ArrayList<>();
        if (spuId != null) {
            result.addAll(attachmentsOf(FileRelationBizTypeEnum.PRODUCT_QUALIFICATION, spuId,
                    TraceAttachmentCategoryEnum.QUALIFICATION));
        }
        // 同一文件即便又被挂为商品资质，也不能绕过检测报告的发布控制。
        List<String> keys = result.stream().map(TraceAttachmentVO::getFileKey).toList();
        if (keys.isEmpty()) {
            return result;
        }
        var reportKeys = fileRelationDao.listActiveByFileKeys(keys).stream()
                .filter(relation -> FileRelationBizTypeEnum.TRACE_INSPECTION_REPORT.name().equals(relation.getBizType())
                        || FileRelationBizTypeEnum.LOT_INSPECTION.name().equals(relation.getBizType()))
                .map(relation -> relation.getFileKey()).collect(Collectors.toSet());
        return result.stream().filter(file -> !reportKeys.contains(file.getFileKey())).toList();
    }

    List<TraceAttachmentVO> reportAttachments(Long reportId) {
        return attachmentsOf(FileRelationBizTypeEnum.TRACE_INSPECTION_REPORT, reportId,
                TraceAttachmentCategoryEnum.INSPECTION);
    }

    /** 某个业务对象当前引用的全部 fileKey：公开下载入口据此判断「这个文件是否属于这个码」。 */
    List<String> attachmentKeysOf(FileRelationBizTypeEnum bizType, Long bizId) {
        return fileRelationDao.listActiveByBiz(bizType.name(), bizId).stream().map(relation -> relation.getFileKey())
                .toList();
    }

    private List<TraceAttachmentVO> attachmentsOf(FileRelationBizTypeEnum bizType, Long bizId,
            TraceAttachmentCategoryEnum category) {
        List<String> keys = fileRelationDao.listActiveByBiz(bizType.name(), bizId).stream()
                .map(relation -> relation.getFileKey()).toList();
        if (keys.isEmpty()) {
            return List.of();
        }
        Map<String, FileVO> metadata = fileService.getFileMetadata(keys).stream()
                .collect(Collectors.toMap(FileVO::getFileKey, Function.identity(), (left, right) -> left));
        List<TraceAttachmentVO> result = new ArrayList<>(keys.size());
        for (String key : keys) {
            FileVO file = metadata.get(key);
            if (file == null) {
                continue;
            }
            TraceAttachmentVO row = new TraceAttachmentVO();
            row.setFileKey(key);
            row.setFileName(file.getFileName());
            row.setFileType(file.getFileType());
            row.setFileSize(file.getFileSize());
            row.setCategory(category.name());
            result.add(row);
        }
        return result;
    }
}
