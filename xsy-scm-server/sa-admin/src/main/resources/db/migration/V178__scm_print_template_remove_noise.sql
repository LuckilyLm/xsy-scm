-- 打印模板减噪：默认模板不再印内部实体编码和解释性页脚。
--
-- 只处理仍是系统初始内容的默认模板：
--   * 销售订单按 version = 0 判断（用户改过任何一处就不再覆盖）；
--   * 其余模板按页脚原文匹配（系统迁移改过版本号，不能只看 version）。
-- 用户自己配置的页脚和字段原样保留。

-- 销售订单：客户编码、商品规格编码是内部实体编号，纸面用不到；页脚是一段通用免责说明。
UPDATE xsy_print_template
SET model = jsonb_set(
                jsonb_set(model - 'footerNote', '{headerFields}', (model -> 'headerFields') - 'customerCode'),
                '{columns}', (model -> 'columns') - 'skuCode'
            ),
    version    = version + 1,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'system'
WHERE document_type = 'SALES_ORDER'
  AND template_code = 'SALES_ORDER_DEFAULT'
  AND deleted = FALSE
  AND version = 0;

-- 线路发货单：空白数量在纸面上本来就能看出未填写，不需要再用页脚解释一遍。
UPDATE xsy_print_template
SET model      = model - 'footerNote',
    version    = version + 1,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'system'
WHERE document_type = 'DELIVERY_NOTE'
  AND template_code = 'DELIVERY_NOTE_DEFAULT'
  AND deleted = FALSE
  AND model ->> 'footerNote' = '本单为配送计划；空白数量表示尚未填写。打印不代表发车，不扣减库存。';

-- 分拣小票：同上，空白实分量与「不回写订单」属于操作口径，不印在给分拣员的纸上。
UPDATE xsy_print_template
SET model      = model - 'footerNote',
    version    = version + 1,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'system'
WHERE document_type = 'SORTING_TICKET'
  AND template_code = 'SORTING_TICKET_DEFAULT'
  AND deleted = FALSE
  AND model ->> 'footerNote' = '空白实分量表示尚未录入（赠品行无需录入）；分拣不回写订单、不改库存。';

-- 包装码标签：标签只留身份与去向，编码规则不作为贴纸上的说明文字。
UPDATE xsy_print_template
SET model      = model - 'footerNote',
    version    = version + 1,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'system'
WHERE document_type = 'TRACE_UNIT'
  AND template_code = 'TRACE_UNIT_LABEL'
  AND deleted = FALSE
  AND model ->> 'footerNote' = '码不透明且永不复用；未绑定分拣行的标签只表示实物身份，不表示已分配给任何客户。';

-- 采购单的「白联 / 红联 / 黄联」是联次分工，属于操作信息，保留。
