# 分拣域

- 业务规则以根目录 `docs/decisions.md` 为准；`SortingTaskService` 是手工录入、分次确认与设备集合完成的共同写入口。
- 开始作业用 `SortingQueryDao.markStarted`，SQL 在 `mapper/scm/sorting/SortingQueryDao.xml`；调用前必须持有任务聚合锁。明细更新仍校验明细版本。
- 调整任务版本时同时检查设备的 `jobVersion` 和已落盘筐记录；回归入口为 `SortingTaskPgIT` 的多商品集合完成场景，改派与重开失效场景不能删。
