-- ============================================================================
-- FA-2b（D-2 落地的第二段）：注册暂存附件回收任务
--
-- 暂存件是「上传成功但业务表单没保存」的产物，必须有界；否则对象存储里会无限堆积
-- 无人认领的私有附件。回收窗口 7 天，与 FileScratchCleanupJob 的默认值一致，
-- 参数留空即取默认，运维改这里就能调窗口而不用改代码。
--
-- 任务只处理 private/common/scratch/ 前缀，并且删除前会再确认
-- 「无关系行 + 无任何业务表直接引用」两个条件（误删物理文件不可逆）。
-- data-only：不改任何表结构，不影响既有业务链路。
-- ============================================================================

INSERT INTO t_smart_job (job_name, job_class, trigger_type, trigger_value, enabled_flag, param, sort, remark,
                         deleted_flag, update_name, create_time, update_time)
SELECT '暂存附件回收',
       'net.lab1024.sa.admin.module.system.support.FileScratchCleanupJob',
       'cron',
       '0 30 3 * * ?',
       TRUE,
       '7',
       90,
       '删除超过 7 天仍未绑定任何业务对象且未被业务表引用的暂存附件',
       FALSE,
       'system',
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1
                  FROM t_smart_job
                  WHERE job_class = 'net.lab1024.sa.admin.module.system.support.FileScratchCleanupJob'
                    AND deleted_flag = FALSE);
