package com.xsy.scm.trace.service;

import com.xsy.scm.trace.dao.TraceInspectionReportDao;
import com.xsy.scm.trace.domain.vo.TracePublicInspectionReportVO;

import java.time.OffsetDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 查询与下载共用同一份数据：仅已发布报告可见，过期报告只返回状态与检测信息。
 */
@Service
@RequiredArgsConstructor
public class TracePublicInspectionService {
    private final TraceInspectionReportDao traceInspectionReportDao;
    private final TraceAttachmentService traceAttachmentService;

    public List<TracePublicInspectionReportVO> reports(Long lotId) {
        OffsetDateTime now = OffsetDateTime.now();
        return traceInspectionReportDao.publishedByLot(lotId).stream().map(report -> {
            TracePublicInspectionReportVO view = new TracePublicInspectionReportVO();
            view.setReportNo(report.getReportNo());
            view.setReportName(report.getReportName());
            view.setInspectionDate(report.getInspectionDate());
            view.setInspectionOrg(report.getInspectionOrg());
            view.setConclusion(report.getConclusion());
            view.setExpiresAt(report.getExpiresAt());
            view.setStatus(report.getStatus());
            view.setExpired(report.getExpiresAt() != null && !report.getExpiresAt().isAfter(now));
            view.setAttachments(
                view.isExpired() ? List.of() : traceAttachmentService.reportAttachments(report.getId()));
            return view;
        }).toList();
    }
}
