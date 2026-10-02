package com.xsy.scm.print.support;

import com.xsy.scm.print.constant.ScmPrintDocumentTypeEnum;

/**
 * 单据类型 → 打印数据源。
 *
 * <p>
 * 每个实现只做一件事：把一张业务单据读成 {@link ScmPrintSource}。读取必须走该域**已有的
 * 查询服务**（因此数据范围与详情页完全同一套判定），不允许打印域自己写 SQL 直连业务表 ——
 * 否则打印会变成一条绕过数据范围的旁路。
 *
 * <p>
 * 实现抛出的「不存在」与「无权查看」错误沿用该域既有错误码与 30005，打印不新增一套回答。
 */
public interface ScmPrintSourceProvider {

    /**
     * 本实现负责的单据类型。
     */
    ScmPrintDocumentTypeEnum documentType();

    /**
     * 打印该类型单据所需的**查看权限码**（与详情页同一权限，例如采购单用
     * {@code scm:purchase:query}）。
     *
     * <p>
     * 必须显式声明并在取数前校验：数据源走的查询服务只做数据范围收窄，不判功能权限，
     * 因此「谁能打印」这一层不能只靠数据范围兜底 —— 否则任何一个登录用户只要知道单据 id
     * 就能把内容打印出来。
     */
    String queryPermission();

    /**
     * 读取一张单据的打印来源；范围或状态不允许时抛该域既有异常。
     */
    ScmPrintSource load(Long businessId);
}
