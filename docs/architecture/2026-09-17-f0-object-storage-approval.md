# F0 Object Storage Activation 批准记录

日期：2026-09-17。批准来源：本轮人类明确裁决。基线：W5.5 完成。

## 16. 人类决策（Human decisions，2026-09-17 已确认）

本次 HD-1～HD-9 已全部裁决；记录后直接按 F0-0 → F0-4 实施，无需再次确认。
本文件与 Target Design §16 同步记录裁决。

| 裁决 | 状态 | 最终决定 |
| --- | --- | --- |
| HD-1 | APPROVED | `private/notice/`、`private/help-doc/` 登录即可读，豁免 creator/ownership；是 authenticated internal resource，绝非 anonymous public。 |
| HD-2 | APPROVED | 仅新增 V17 data-only migration，将 `t_config.level3_protect_config.maxUploadFileSizeMb` 对齐为 20；V1–V16 禁止修改。 |
| HD-3 | APPROVED WITH FOLLOW-UP DEBT | `private/common/` 自 F0 起严格执行 administrator OR `support:file:query` OR creator 为当前员工，否则 HTTP 403。正式引入任何非管理员业务角色前，OA 企业证照等 COMMON 私密资产必须迁到业务 permission + ownership/relation + FileService；禁止为兼容页面再次放宽 COMMON。 |
| HD-4 | OVERRIDE RECOMMENDATION | 不新增 dev-minio Maven/resources profile；复用 dev，local 默认不变，cloud/MinIO 全部通过 ENV 激活，避免扩大 `${profiles.active}` 的 dev/test/pre/prod 资源规则。 |
| HD-5 | APPROVED WITH SAFETY RULE | `.env.example` 可给仅限本地开发的示例凭据；compose 不含隐式生产 fallback；真实 `.env` 必须 gitignore；pre/prod 禁止复用示例凭据。 |
| HD-6 | APPROVED WITH VERSION POLICY | 禁止 latest、旧 server `RELEASE.2025-09-07T16-13-09Z` 与第三方 MinIO canonical 镜像。官方源码 tag `RELEASE.2025-10-15T17-29-55Z` 构建为 `xsy-scm/minio:RELEASE.2025-10-15T17-29-55Z`；mc 固定 `quay.io/minio/mc:RELEASE.2025-08-13T08-35-41Z`（2026-09-17 验收修正：Docker Hub 该仓库已 404，改官方 quay.io，tag 与摘要不变）。允许 deploy/minio 下新增构建文件。MinIO 仅为 dev/integration S3-compatible target，不承诺生产选型。 |
| HD-7 | APPROVED AS ENV-DRIVEN | `XSY_FILE_PUBLIC_URL_PREFIX` 可覆盖，禁止永久写死 127.0.0.1；本机示例为 `http://127.0.0.1:9000/xsy-scm-dev/`，手机/其他电脑改用可达 LAN IP、hostname 或 domain。本机 Playwright 可用 127.0.0.1。 |
| HD-8 | APPROVED | AGENTS 描述真实 `deploy/minio/`，明确当前不存在根 docker-compose.yml、xsy-device-agent/。 |
| HD-9 | APPROVED | 不给非管理员预置 `support:file:query`；无正式非管理员种子角色，授权留正式 RBAC/业务角色设计。 |

### HD-2 补记：版本号在合并 W5.5 后重定（2026-09-17）

HD-2 的批准内容**不变**——F0 新增的那一个 data-only migration 仍是 **`V17__sa_config_file_upload_size.sql`**，
V1–V16 仍禁止修改。

需要补记的是**版本号占用冲突**：W5.5 波次的独立增量 `fdfd643` 也把一个 migration 命名为 `V17`
（`V17__scm_menu_icons.sql`，侧边栏图标）。F0 的 V17 创建于 15:15:51（当时工作区上限为 V16），
而 `fdfd643` 于 16:01:33 推上远端占用 V17；我的本地基线在 17:24:55 才 fast-forward 追上，
提交时未复核上限，导致仓库同时存在两个 V17。

**裁决与处置**：按「保留已被真实库应用过的版本号」原则，**F0 的 V17 保持不变**
（开发库 `flyway_schema_history` 中 `version 17 = V17__sa_config_file_upload_size.sql`，
checksum `998995225`，已成功应用），把 **W5.5 的 `V17__scm_menu_icons.sql` 改名为
`V18__scm_menu_icons.sql`**（内容字节不变）。因此：

```text
V17  V17__sa_config_file_upload_size.sql   F0    ← HD-2 批准的这个，未动
V18  V18__scm_menu_icons.sql               W5.5  ← 由 W5.5 的 V17 重编号而来
```

**全程未执行 `flyway repair`，未改写 `flyway_schema_history` 任何一行。** 完整审计、修复方案
与 fresh-DB 实测见 [V17 Flyway 版本冲突审计报告](./2026-09-17-v17-migration-conflict-audit.md)。
F0 自身迁移的清单见 [`f0-applied-migrations.sha256`](./f0-applied-migrations.sha256)。

### 强制后续债务 F0-DEBT-01

在正式引入任何非管理员业务角色之前，必须验收 OA 企业证照等 COMMON 私密资产的
业务 permission + ownership/relation + FileService 读取链路。该事项是角色上线前置门槛，
不能通过放宽 `/file/getFileUrl` 或 `/file/downLoad` 的 COMMON 门禁消除。
F0 只验证管理员、文件查询权限持有者、上传者的允许路径与其他员工的 403；
并不宣称所有未来 OA 读者已拥有正确业务关系授权。

---

## 执行授权与边界

- 记录后直接按 F0-0 → F0-4 实施，不再等待确认；全量回归通过后停止，不自动进入 W6。
- 同步 AGENTS 的 W5.5/F0 状态、实际结构及 W5.5 文档索引。
- 不修改 W1–W5 业务行为；不新增 product_media；product_image 保持正式商品图片模型。
- 不修改 V1–V16、不改 reference、不预置正式非管理员角色权限。
- cloud/MinIO 才要求 Presigned 验收；local 只要求 regression。
- 交付 `docs/architecture/2026-09-17-f0-object-storage-验收报告.md`，以实测证据判定完成，不以批准替代验收。

设计依据：[Target Design](./2026-09-17-f0-object-storage-target-design.md)。

## 后续执行指令（同日）

人类补充：「继续完成，你不需要进行测试了我会让别人测」。
该指令将测试执行交接给外部人员，不撤销 HD-1～HD-9，也不降低原 DoD。
本执行者继续交付代码、部署文件和测试资产；不再运行构建/测试/集成/回归。
F0 必须保持未验收，待外部真实 cloud/MinIO 与完整回归证据齐备后才能判定 COMPLETE。
W6 继续 NOT STARTED。
