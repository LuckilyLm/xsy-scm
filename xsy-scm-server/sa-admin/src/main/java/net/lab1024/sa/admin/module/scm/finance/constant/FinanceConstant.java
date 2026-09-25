package net.lab1024.sa.admin.module.scm.finance.constant;

/**
 * 财务域的固定口径：单号前缀、权限码与锁序 rank。
 *
 * <p><b>金额与数量一律 {@code NUMERIC(18,4)}、scale 4、{@code HALF_UP}</b>（Q22）；
 * 序列化沿用 {@code ScmFixedScale4Serializer}（null 写 null，绝不写 0）。
 * 展示可以按 2 位格式化，但存储与对账口径恒为 4 位。
 *
 * <p><b>财务域没有状态机</b>（Q17 / Q20）：这里刻意没有任何状态常量集合 ——
 * 结清 / 部分核销 / 待核销都是读时派生视图，落库就会多出一个会漂移的副本。
 */
public final class FinanceConstant {

    /**
     * 应收单号前缀：AR + 业务日(Asia/Shanghai) + 全局非重置序号，拼接走
     * {@code ScmDocumentNumbers.format}。
     */
    public static final String RECEIVABLE_NO_PREFIX = "AR";

    /**
     * 应付单号前缀。
     */
    public static final String PAYABLE_NO_PREFIX = "AP";

    /**
     * 收款单号前缀。
     */
    public static final String RECEIPT_NO_PREFIX = "RC";

    /**
     * 付款单号前缀。
     */
    public static final String PAYMENT_NO_PREFIX = "PM";

    /**
     * 核销单号前缀。
     */
    public static final String WRITE_OFF_NO_PREFIX = "WO";

    public static final String RECEIVABLE_QUERY_PERM = "scm:finance:receivable:query";
    public static final String PAYABLE_QUERY_PERM = "scm:finance:payable:query";
    public static final String RECEIPT_QUERY_PERM = "scm:finance:receipt:query";
    public static final String PAYMENT_QUERY_PERM = "scm:finance:payment:query";
    public static final String WRITE_OFF_QUERY_PERM = "scm:finance:write-off:query";

    public static final String RECEIPT_ADD_PERM = "scm:finance:receipt:add";
    public static final String PAYMENT_ADD_PERM = "scm:finance:payment:add";
    public static final String WRITE_OFF_ADD_PERM = "scm:finance:write-off:add";
    public static final String PAYABLE_RED_PERM = "scm:finance:payable:red";

    /**
     * 四个破坏性动作各一条独立权限（Q20 / D-3）：能登记一笔款的人不必然是能冲掉一笔款的人，
     * 因此它们既不与 {@code *:add} 合并，也不合成一个 {@code scm:finance:reverse}。
     */
    public static final String WRITE_OFF_REVERSE_PERM = "scm:finance:write-off:reverse";
    public static final String RECEIPT_REVERSE_PERM = "scm:finance:receipt:reverse";
    public static final String PAYMENT_REVERSE_PERM = "scm:finance:payment:reverse";

    /**
     * 导出许可，<b>绝不扩大查询范围</b>（P0 裁决 10）：导出端点要求
     * 「对应 {@code *:query} AND 本权限」，且与列表共用同一次范围解析结果。
     */
    public static final String EXPORT_PERM = "scm:finance:export";

    /*
     * 以上权限串是设计稿 §16 冻结的**词汇表**，本阶段（F1-1）一条都还没有发布到 t_menu：
     * F1-1 只有 schema 与骨架，没有任何 Controller，因此不存在需要授权的受保护端点。
     * 提前种权限点会让「已授权但无任何端点使用它」出现在生产库里，而 menu_id 一旦被真实库
     * 应用就不可回收，等于用一个永久的号段去换一个不存在的能力。
     *
     * 发布时机（每个阶段只种自己第一次真正用到的那些）：
     *   F1-2 生成器      —— 不挂权限点：应收 / 应付 / 红字应收是业务事务内的派生写，
     *                       权限由触发命令（签收 / 收货确认 / 退货批准）的既有权限承担。
     *   F1-3 收付款登记  —— RECEIPT_ADD_PERM / PAYMENT_ADD_PERM 及其查询权限
     *   F1-4 核销与红字  —— WRITE_OFF_ADD_PERM / WRITE_OFF_REVERSE_PERM / PAYABLE_RED_PERM /
     *                       RECEIPT_REVERSE_PERM / PAYMENT_REVERSE_PERM
     *   F1-5 查询与导出  —— 五个 *_QUERY_PERM 与 EXPORT_PERM
     *   F1-6 前端页面    —— 五个页面菜单（1501–1505）与目录 1500，且 component 必须真实存在
     *
     * 刻意不含任何 {@code *:scope:all:query}：财务的全范围来自既有显式授权
     * （1302 / 1311 / 1322 / 1331），本期不新增范围放宽点（D-5）。
     * D-1 不回填，因此也没有任何历史补生成权限。
     *
     * 号段 1500–1531 只是**规划**，每次落库前必须重扫 t_menu 实际占用（AGENTS.md 同一条纪律）。
     */

    /**
     * 写命令的加锁层级（设计稿 §14）：插入既有纪律「单据锁先于余额锁、余额锁最后」之后。
     *
     * <p>所有用户命令按 rank 升序、同 rank 按 id 升序 {@code SELECT … FOR UPDATE}。
     * 收付款反向与核销**共用同一把原收付款行锁**（rank 1 / 2），因此两者天然串行 ——
     * 这正是 D-3「反向前已用额必须 = 0」能被并发安全校验的前提，
     * 「先查再判断」的乐观写法在这里不成立。
     *
     * <p>财务域**不获取**任何 {@code inventory_balance} 行锁（全局不变量 4）。
     */
    public static final int LOCK_RANK_RECEIPT = 1;
    public static final int LOCK_RANK_PAYMENT = 2;
    public static final int LOCK_RANK_RECEIVABLE = 3;
    public static final int LOCK_RANK_PAYABLE = 4;
    public static final int LOCK_RANK_WRITE_OFF = 5;

    /**
     * 财务事实的统一精度：scale 4（Q22）。
     */
    public static final int AMOUNT_SCALE = 4;

    private FinanceConstant() {
    }
}
