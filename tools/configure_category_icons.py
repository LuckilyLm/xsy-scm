"""显式补齐交付图标对应的空分类，不移动商品，不覆盖已有分类图标。"""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import subprocess
from urllib.parse import urlparse
from urllib.request import urlopen

from minio import Minio
from minio.error import S3Error


def sql_text(value):
    """将配置名称作为SQL字面量传入，不作为标识符或命令拼接。"""
    return "'" + value.replace("'", "''") + "'"


def main():
    """先上传并核对图片，再在一笔事务中补齐分类；重复运行保留已有人工配置。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--database', required=True)
    parser.add_argument('--container', default='xsy-scm-postgres-1')
    parser.add_argument('--user', default='xsy_scm_app')
    parser.add_argument('--endpoint', required=True)
    parser.add_argument('--bucket', required=True)
    parser.add_argument('--public-url-prefix', required=True)
    parser.add_argument('--report', required=True)
    parser.add_argument('--apply', action='store_true', help='不传时仅输出分类计划，不上传或修改数据库')
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    assets = root / 'xsy-scm-web/src/assets/category-icons'
    sources = json.loads((assets / 'sources.json').read_text(encoding='utf-8'))
    roots = [('生鲜蔬菜', 'vegetables'), ('生鲜水果', 'fruit'), ('肉禽蛋品', 'meat-eggs'),
             ('水产', 'fish-shrimp'), ('粮油', 'grain-oil'), ('调味', 'seasoning')]
    children = {
        '生鲜蔬菜': [('叶菜类', 'leafy'), ('根茎类', 'roots'), ('瓜果类', 'gourds'), ('菌菇类', 'mushrooms')],
        '生鲜水果': [('国产水果', 'domestic-fruit'), ('进口水果', 'imported-fruit')],
        '肉禽蛋品': [('禽蛋类', 'poultry'), ('牛羊肉', 'beef-lamb'), ('猪肉', 'pork')],
        '水产': [('鱼虾', 'fish-shrimp'), ('虾蟹', 'shrimp-crab'), ('冻品', 'frozen')],
        '粮油': [('米面', 'rice-flour'), ('食用油', 'cooking-oil'), ('杂粮', 'grains')],
        '调味': [('基础调味', 'seasoning'), ('香辛料', 'spices')],
    }
    if not args.apply:
        print(json.dumps({'roots': roots, 'children': children}, ensure_ascii=False))
        return
    endpoint = urlparse(args.endpoint)
    if endpoint.scheme not in ('http', 'https') or not endpoint.hostname or endpoint.username or endpoint.path not in ('', '/'):
        raise SystemExit('存储地址必须是无账号信息的http或https服务地址')
    client = Minio(endpoint.netloc, access_key=os.environ['XSY_FILE_ACCESS_KEY'], secret_key=os.environ['XSY_FILE_SECRET_KEY'], secure=endpoint.scheme == 'https')
    commands = ['BEGIN;', 'SET LOCAL search_path TO xsy,public;', 'LOCK TABLE xsy_product_category IN SHARE ROW EXCLUSIVE MODE;']
    keys = {}
    for item in sources:
        content = (assets / item['file']).read_bytes()
        digest = hashlib.sha256(content).hexdigest()
        if digest != item['sha256']:
            raise SystemExit('图标与来源清单不一致：' + item['file'])
        key = f"public/image/category-icons/{digest}.png"
        keys[Path(item['file']).stem] = key
        try:
            client.stat_object(args.bucket, key)
        except S3Error as error:
            if error.code not in ('NoSuchKey', 'NoSuchObject'):
                raise
            client.put_object(args.bucket, key, io.BytesIO(content), len(content), content_type='image/png')
        with urlopen(args.public_url_prefix.rstrip('/') + '/' + key, timeout=20) as response:
            if hashlib.sha256(response.read()).hexdigest() != digest:
                raise SystemExit('图标公开读取校验失败：' + item['file'])
        commands.append('INSERT INTO t_file(folder_type,file_name,file_size,file_key,file_type,creator_name) VALUES '
                        f"(2,{sql_text('分类图标-' + item['name'] + '.png')},{len(content)},{sql_text(key)},'png','系统配置') ON CONFLICT(file_key) DO NOTHING;")
    # 临时函数只活在本连接；同级同名不确定时整笔拒绝，不能任意选中一条。
    commands.append('''CREATE FUNCTION pg_temp.configure_category(p bigint,n text,k text) RETURNS bigint LANGUAGE plpgsql AS $$
DECLARE found_id bigint; matches integer;
BEGIN
  SELECT count(*), min(id) INTO matches,found_id FROM xsy_product_category WHERE deleted=FALSE AND parent_id IS NOT DISTINCT FROM p AND name=n;
  IF matches>1 THEN RAISE EXCEPTION '存在同级同名分类，请先核对：%',n; END IF;
  IF found_id IS NULL THEN
    INSERT INTO xsy_product_category(parent_id,category_code,name,level,sort_order,status,icon_file_key,created_by,updated_by)
    VALUES(p,'CAT'||to_char(nextval('product_category_code_seq'),'FM000000'),n,CASE WHEN p IS NULL THEN 1 ELSE 2 END,
      100,'ENABLED',k,'系统配置','系统配置') RETURNING id INTO found_id;
  ELSE
    UPDATE xsy_product_category SET icon_file_key=k,version=version+1,updated_at=CURRENT_TIMESTAMP,updated_by='系统配置'
      WHERE id=found_id AND icon_file_key IS NULL;
  END IF;
  RETURN found_id;
END $$;''')
    for name, icon in roots:
        parent = f"pg_temp.configure_category(NULL,{sql_text(name)},{sql_text(keys[icon])})"
        commands.append(f'SELECT {parent};')
        for child, child_icon in children[name]:
            commands.append(f'SELECT pg_temp.configure_category({parent},{sql_text(child)},{sql_text(keys[child_icon])});')
    # 已有禽蛋末级保留归属，分别配置蛋品和禽肉图，不能把同级节点合并。
    commands.append(f"UPDATE xsy_product_category c SET icon_file_key={sql_text(keys['eggs'])},version=version+1,updated_at=CURRENT_TIMESTAMP "
                    "WHERE c.name='蛋类' AND c.deleted=FALSE AND c.icon_file_key IS NULL AND EXISTS "
                    "(SELECT 1 FROM xsy_product_category p WHERE p.id=c.parent_id AND p.name='禽蛋类' AND p.deleted=FALSE);")
    commands.append('COMMIT;')
    result = subprocess.run(['docker', 'exec', '-i', args.container, 'psql', '-X', '-v', 'ON_ERROR_STOP=1', '-U', args.user, '-d', args.database],
                            input='\n'.join(commands), text=True, encoding='utf-8', capture_output=True)
    if result.returncode:
        raise SystemExit(result.stderr)
    report = {'database': args.database, 'presetImages': len(keys), 'roots': len(roots), 'configuredChildren': sum(map(len, children.values())),
              'existingIconsPreserved': True, 'productAssignmentsChanged': False}
    Path(args.report).write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report, ensure_ascii=False))


if __name__ == '__main__':
    main()
