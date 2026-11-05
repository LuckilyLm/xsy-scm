-- 只更新系统自带任务的执行类名称，保留任务编号、时间、启停状态和业务配置。
UPDATE xsy_v2.t_smart_job AS job
SET job_class = names.current_name
FROM (VALUES
    ('net.lab1024.sa.base.module.support.job.sample.SmartJobSample1',
     'com.xsy.scm.base.module.support.job.sample.XsyJobSample1'),
    ('net.lab1024.sa.base.module.support.job.sample.SmartJobSample2',
     'com.xsy.scm.base.module.support.job.sample.XsyJobSample2'),
    ('net.lab1024.sa.admin.module.system.support.FileScratchCleanupJob',
     'com.xsy.scm.admin.module.system.support.FileScratchCleanupJob')
) AS names(previous_name, current_name)
WHERE job.job_class = names.previous_name;
