-- 只更新 V3 播种且未被管理员改名的默认部门，保留正式业务资料中的自定义名称。
UPDATE xsy_v2.t_department
SET department_name = '鲜蔬园',
    update_time = now()
WHERE department_id = 1
  AND department_name = '鲜蔬源';

COMMENT ON COLUMN xsy_v2.t_notice.document_number IS '文号，如：鲜蔬园〔2026〕字第36号';

-- 已发布的系统帮助条目仅在仍保留旧标题时更名，不覆盖管理员编辑的内容。
UPDATE xsy_v2.t_help_doc
SET title = '微信小程序：下单与支付'
WHERE help_doc_id = 17
  AND author = 'system'
  AND title = '客户商城：下单与支付';
