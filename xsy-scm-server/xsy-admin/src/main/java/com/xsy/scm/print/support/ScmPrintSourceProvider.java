package com.xsy.scm.print.support;

import com.xsy.scm.print.constant.ScmPrintDocumentTypeEnum;

/**
 * 单据类型对应的打印数据源。
 *
 * <p>
 * 每个实现只做一件事：把一张业务单据读成 {@link ScmPrintSource}。取数必须走该域已有的查询服务，数据范围与详情页一致； 打印域不自己写 SQL 直连业务表，否则会绕过数据范围。
 *
 * <p>
 * 「不存在」与「无权查看」沿用该域既有的错误码与 30005。
 */
public interface ScmPrintSourceProvider {

    /** 本实现负责的单据类型。 */
    ScmPrintDocumentTypeEnum documentType();

    /**
     * 打印该类型单据所需的查看权限码（与详情页同一权限，例如采购单用 {@code scm:purchase:query}）。
     *
     * <p>
     * 必须在取数前校验：查询服务只做数据范围收窄，不判功能权限，只靠数据范围兜底的话， 任何登录用户知道单据 id 就能把内容打印出来。
     */
    String queryPermission();

    /** 读取一张单据的打印来源；范围或状态不允许时抛该域既有异常。 */
    ScmPrintSource load(Long businessId);

    /** 只校验当前数据范围；历史重印不看当前能否打印，也不重算内容。 */
    void requireVisible(Long businessId);

    /**
     * 单据所属客户，用于解析客户专属模板；跨客户单据返回 {@code null}。
     *
     * <p>
     * 必须在 {@link #requireVisible(Long)} 之后调用：客户归属信息也只能从该域带数据范围的查询里取。
     */
    default Long customerId(Long businessId) {
        return null;
    }
}
