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
     * 收款登记的幂等 scope（与 {@code Idempotency-Key} 一起定位一条命令）。
     *
     * <p>{@code OrderIdempotencyService} 会自动按「登录身份 : scope」再加一层前缀，
     * 因此这里只写动作名，不要重复拼员工 id。命名沿用设计稿 §13 的 {@code "<动作>:<业务主键>"} 家族。
     */
    public static final String RECEIPT_ADD_SCOPE = "FINANCE_RECEIPT_ADD";

    /**
     * 付款登记的幂等 scope（与 {@code Idempotency-Key} 一起定位一条命令），设计稿 §13 命名。
     *
     * <p>它防的是<b>重复请求</b>；退款付款还多一层<b>重复事实</b>防护
     * （{@code uk_finance_payment_source_active}），所以两个不同幂等键同时付同一张退款
     * 也只会在库里留下一笔付款。供应商付款没有来源列，只有前者这一层。
     */
    public static final String PAYMENT_ADD_SCOPE = "FINANCE_PAYMENT_ADD";

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
     * 以上权限串是设计稿 §16 冻结的**词汇表**。发布纪律：一个阶段只种它第一次真实使用的能力，
     * 且**没有 .vue 就不种页面菜单**（menu_id 一旦被真实库应用就不可回收，
     * 而页面菜单的 component 缺失会让用户点开空白页、构建与测试却全绿）。
     *
     * 已发布：V66（F1-3A）= 1500 隐藏目录「财务管理」（menu_type=1、无组件、不可见）
     * + 1521 scm:finance:receipt:add；V67（F1-3B）= 1522 scm:finance:payment:add（父级 1500 已建）。
     * 这是本仓库既有的「只有 API 能力、还没有页面」正式范式
     * （V28 数据大屏 900/901、V46 业务待办 1100/1101 同形）：目录不注册组件，因此不存在
     * 空白页风险，而能力点可以正常出现在角色管理界面里被分配。
     * 1500 的 visible_flag 与五个页面菜单一起在 F1-6 打开。
     *
     * 待各阶段发布（判据是「该权限第一次被真实端点使用」，不是阶段名字）：
     *   F1-2 生成器      —— 不挂权限点：应收 / 应付 / 红字应收是业务事务内的派生写，
     *                       权限由触发命令（签收 / 收货确认 / 退货批准）的既有权限承担。
     *   F1-3C 收付款反向 —— RECEIPT_REVERSE_PERM / PAYMENT_REVERSE_PERM
     *   F1-4 核销与红字  —— WRITE_OFF_ADD_PERM / WRITE_OFF_REVERSE_PERM / PAYABLE_RED_PERM
     *   F1-5 查询与导出  —— 五个 *_QUERY_PERM 与 EXPORT_PERM。
     *                       RECEIPT_QUERY_PERM(1513) / PAYMENT_QUERY_PERM(1514) 设计稿原标 F1-3，
     *                       但 F1-3A / F1-3B 只有写入口、没有查询端点，故不提前种
     *                       （§16 已按此订正，V66 / V67 各只种自己那一行）。
     *   F1-6 前端页面    —— 五个页面菜单 1501–1505（component 必须真实存在）
     *
     * 刻意不含任何 {@code *:scope:all:query}：财务的全范围来自既有显式授权
     * （V56 授 1302 / 1311 / 1322，V57 补 1331），本期不新增范围放宽点（D-5）。
     * D-1 不回填，因此也没有任何历史补生成权限。
     *
     * 号段 1500–1531 是**规划值**，每次落库前必须重扫 t_menu 实际占用（AGENTS.md 同一条纪律）。
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
