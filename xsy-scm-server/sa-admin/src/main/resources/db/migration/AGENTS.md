# Flyway迁移

- V1001移除业务触发器、CHECK和专用函数，不写业务行或重置序列；已冻结模块SQL不能为此改写，新库与旧库统一追加迁移。

- 正式规则以根目录docs/decisions.md为准，部署路线见deploy/README.md。
- 已应用V脚本和已使用B基线均保持字节稳定，新增变更追加V版本；禁止为了修SQL或格式化而改历史校验和。
- 当前初始化在modules子目录中，包含24份模块SQL（V1000.01～V1000.24），一模块一文件，定义截至原V244的最终结构和默认数据；不保留历史增删字段、重命名或回填。
- 旧V/B SQL已退役，由Git追溯，不重新放入资源或制品。旧库须先用tools/cutover_schema_baseline.py核对历史与最终结构，显式保数据切换后才能启动新链；不能关闭validate或自动baseline接管。
- 模块SQL及checksum发布后冻结；后续上线差异追加V1001及更高版本。tools/install_baseline_manifest.json为模块归属与内容完整性清单，不用修改CREATE TABLE冒充旧库升级。
