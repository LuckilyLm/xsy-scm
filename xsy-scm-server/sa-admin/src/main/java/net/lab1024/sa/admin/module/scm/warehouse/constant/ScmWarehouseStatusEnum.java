package net.lab1024.sa.admin.module.scm.warehouse.constant;

/**
 * 仓库状态（2 值）。
 *
 * <p>设计依据：W5 Target Design §2.1 与附录 B.1（warehouse 域持有自己的状态枚举）。
 * 取值与 V15 的 {@code ck_warehouse_status} 白名单逐字一致。
 *
 * <p>取值集合与 {@code common/constant/ScmEnableStatusEnum} 相同。两者并存是刻意的：
 * {@code common/**} 属 W1–W4 已验收代码（**零修改**），而 warehouse 作为**独立主数据域**
 * 需要自己的状态词汇，以便 W6 库存域直接依赖它而不反向依赖 common 之外的任何包。
 * 后续若要合并，应作为一次不改变对外行为的纯重构单独提出。
 */
public enum ScmWarehouseStatusEnum {ENABLED, DISABLED}
