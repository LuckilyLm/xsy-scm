"""PostgreSQL最终结构指纹；忽略业务行、序列当前位置、owner与访问授权。"""
import hashlib
import json
import re


# search_path由调用方固定为xsy_v2,pg_catalog，防止regclass展示受连接配置影响。
CATALOG_SQL = """
WITH tables AS (
 SELECT c.oid, c.relname FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
 WHERE n.nspname='xsy_v2' AND c.relkind IN ('r','p')
   AND c.relname NOT IN ('flyway_schema_history','flyway_schema_history_legacy')
), objects AS (
 SELECT 'table:'||t.relname AS key, jsonb_build_object(
   'kind', c.relkind, 'persistence', c.relpersistence, 'row_security',c.relrowsecurity,
   'force_row_security',c.relforcerowsecurity,'comment',obj_description(c.oid,'pg_class')) AS value
 FROM tables t JOIN pg_class c ON c.oid=t.oid
 UNION ALL
 SELECT 'column:'||t.relname||'.'||a.attname, jsonb_build_object(
   'position', row_number() OVER (PARTITION BY t.oid ORDER BY a.attnum),
   'type', format_type(a.atttypid,a.atttypmod), 'not_null', a.attnotnull,
   'collation', (SELECT n.nspname||'.'||c.collname FROM pg_collation c
                 JOIN pg_namespace n ON n.oid=c.collnamespace WHERE c.oid=a.attcollation),
   'identity', a.attidentity, 'generated', a.attgenerated,
   'default', pg_get_expr(d.adbin,d.adrelid), 'comment', col_description(t.oid,a.attnum))
 FROM tables t JOIN pg_attribute a ON a.attrelid=t.oid AND a.attnum>0 AND NOT a.attisdropped
 LEFT JOIN pg_attrdef d ON d.adrelid=t.oid AND d.adnum=a.attnum
 UNION ALL
 SELECT 'constraint:'||t.relname||'.'||c.conname, jsonb_build_object(
   'type',c.contype,'definition',pg_get_constraintdef(c.oid),'validated',c.convalidated,
   'deferrable',c.condeferrable,'deferred',c.condeferred,'comment',obj_description(c.oid,'pg_constraint'))
 FROM tables t JOIN pg_constraint c ON c.conrelid=t.oid
 UNION ALL
 SELECT 'index:'||i.relname, jsonb_build_object('table',t.relname,'definition',pg_get_indexdef(i.oid),
   'valid',x.indisvalid,'ready',x.indisready,'comment',obj_description(i.oid,'pg_class'))
 FROM tables t JOIN pg_index x ON x.indrelid=t.oid JOIN pg_class i ON i.oid=x.indexrelid
 UNION ALL
 SELECT 'trigger:'||t.relname||'.'||g.tgname, jsonb_build_object('definition',pg_get_triggerdef(g.oid),
   'enabled',g.tgenabled,'comment',obj_description(g.oid,'pg_trigger'))
 FROM tables t JOIN pg_trigger g ON g.tgrelid=t.oid AND NOT g.tgisinternal
 UNION ALL
 SELECT 'function:'||p.proname||'('||pg_get_function_identity_arguments(p.oid)||')',
   jsonb_build_object('definition',pg_get_functiondef(p.oid),'comment',obj_description(p.oid,'pg_proc'))
 FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='xsy_v2'
 UNION ALL
 SELECT 'sequence:'||c.relname, jsonb_build_object('type',format_type(s.seqtypid,NULL),
   'start',s.seqstart,'increment',s.seqincrement,'max',s.seqmax,'min',s.seqmin,
   'cache',s.seqcache,'cycle',s.seqcycle,'owned_by',t.relname||'.'||a.attname,
   'comment',obj_description(c.oid,'pg_class'))
 FROM pg_sequence s JOIN pg_class c ON c.oid=s.seqrelid
 JOIN pg_namespace n ON n.oid=c.relnamespace
 LEFT JOIN pg_depend d ON d.objid=c.oid AND d.classid='pg_class'::regclass AND d.deptype IN ('a','i')
 LEFT JOIN tables t ON t.oid=d.refobjid
 LEFT JOIN pg_attribute a ON a.attrelid=t.oid AND a.attnum=d.refobjsubid
 WHERE n.nspname='xsy_v2'
)
SELECT coalesce(jsonb_object_agg(key,value),'{}'::jsonb) FROM objects
"""


def normalize_definition(definition: str) -> str:
    """归一PG改列与新建表生成的等价文本数组转换；其余SQL原样保留。

    参数必须为pg_get_constraintdef/pg_get_indexdef结果；只接受无长度限制的
    varchar字面量数组整体转text[]，或逐个字面量转text。类型长度、枚举值、
    顺序和其他表达式均不丢弃，字符串内部内容不作为SQL解析。
    """
    tokens = list(re.finditer(r"'(?:''|[^'])*'|\"(?:\"\"|[^\"])*\"|::|[A-Za-z_][A-Za-z0-9_$]*|\S", definition))
    values = [token.group() for token in tokens]
    replacements = []
    for start, token in enumerate(values):
        if token.upper() != 'ARRAY' or values[start + 1:start + 2] != ['[']:
            continue
        index = start + 2
        literals, kinds = [], []
        while index < len(values):
            wrapped = values[index] == '('
            if wrapped:
                index += 1
            if index >= len(values) or not values[index].startswith("'"):
                break
            literal = values[index]
            index += 1
            if values[index:index + 3] == ['::', 'character', 'varying']:
                kind = 'varchar'
                index += 3
            elif values[index:index + 2] == ['::', 'text']:
                kind = 'text'
                index += 2
            else:
                break
            if wrapped:
                if kind != 'varchar' or values[index:index + 3] != [')', '::', 'text']:
                    break
                kind = 'text'
                index += 3
            literals.append(literal)
            kinds.append(kind)
            if values[index:index + 1] == [',']:
                index += 1
                continue
            if values[index:index + 1] != [']']:
                break
            end = index
            # pg_dump会保留ALTER TYPE形成的数组整体转换，新建时PG会分配到各元素。
            outer_cast = start > 0 and values[start - 1] == '(' and values[end + 1:end + 6] == [')', '::', 'text', '[', ']']
            if not outer_cast and any(kind != 'text' for kind in kinds):
                break
            first = start - 1 if outer_cast else start
            last = end + 5 if outer_cast else end
            canonical = 'ARRAY[' + ', '.join(value + '::text' for value in literals) + ']'
            replacements.append((tokens[first].start(), tokens[last].end(), canonical))
            break
    for start, end, canonical in reversed(replacements):
        definition = definition[:start] + canonical + definition[end:]
    return definition


def fingerprints(catalog: dict) -> dict:
    """逐对象规范化JSON并计算摘要；参数为只读目录查询结果，返回无业务数据的签名。"""
    def normalize(value):
        if isinstance(value, str):
            return value.replace('\r\n', '\n').replace('\r', '\n')
        if isinstance(value, dict):
            return {key: normalize(item) for key, item in value.items()}
        if isinstance(value, list):
            return [normalize(item) for item in value]
        return value

    result = {}
    for key, value in sorted(catalog.items()):
        value = normalize(value)
        if key.startswith(('constraint:', 'index:')) and isinstance(value.get('definition'), str):
            value['definition'] = normalize_definition(value['definition'])
        result[key] = hashlib.sha256(json.dumps(value, ensure_ascii=False, sort_keys=True,
            separators=(',', ':')).encode('utf-8')).hexdigest()
    return result


def differences(expected: dict, actual: dict) -> dict:
    """比较对象签名；缺失、增加和定义差异均需处理，不能自动接管。"""
    return {'missing': sorted(expected.keys() - actual.keys()),
            'extra': sorted(actual.keys() - expected.keys()),
            'changed': sorted(key for key in expected.keys() & actual.keys() if expected[key] != actual[key])}
