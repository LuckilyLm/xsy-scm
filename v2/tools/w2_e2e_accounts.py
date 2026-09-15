"""Provision/remove uniquely named local W2 test users. Credentials stay in process memory.

Copy First + Adapt from `w1_e2e_accounts.py`（W1 已验收，不改动它）：

* 账号名前缀 `w2_e2e_`；
* 菜单集合换成 W2 的 431–482（客户 / 供应商），**外加 W1 的 401–424** ——
  供应商 E2E 需要先用商品接口造一个真实上架 SKU 才能测「关联商品」，
  而造商品需要 `scm:product:*` / `scm:product:category:*` 权限。
  这只影响测试夹具账号，不影响任何生产账号或 W1 已验收代码。
* 只读账号只给 W2 的**查询类**权限（页面 + query），用于验证「按钮被隐藏 + 接口 30005」。
"""
import os
import re
import subprocess
import sys
from argon2 import PasswordHasher

name = os.environ['W2_E2E_NAME']
if not re.fullmatch(r'w2_e2e_[a-z0-9_]{1,15}', name):
    raise ValueError('Unexpected fixture account name')
names = [name, name + '_read']
quoted = ','.join("'" + value + "'" for value in names)

# W1 商品域（造 SKU 夹具所需）+ W2 客户 / 供应商域全量。
W2_FULL = ','.join(
    [
        # W1 商品域：仅为造夹具数据
        '401', '402', '403', '404',
        '411', '412', '413', '414', '415', '416',
        '421', '422', '423', '424',
        # W2 客户域
        '431', '432', '433', '434',
        '441', '442', '443', '444', '445',
        '451', '452', '453', '454',
        # W2 供应商域
        '461', '462', '463', '464',
        '471', '472', '473', '474', '475',
        '481', '482',
        # W3 SKU option read capability used by the existing supplier page
        '425',
    ]
)
# 只读：W2 页面 + 查询权限，不含任何 add / update / status / delete。
W2_READ = '431,432,433,434,441,451,461,462,463,464,471,481,425'

if sys.argv[1] == 'setup':
    statements = ['BEGIN; SET LOCAL search_path=xsy_v2;']
    for account, menus in [(names[0], W2_FULL), (names[1], W2_READ)]:
        # EmployeeService.generateSaltPassword then SecurityPasswordService's Argon2 v5_8 parameters.
        salted = os.environ['W2_E2E_PASSWORD'] + '_' + account.upper() + '_' + account.lower()
        hashed = PasswordHasher(time_cost=2, memory_cost=16384, parallelism=1, hash_len=32, salt_len=16).hash(salted)
        statements += [
            f"INSERT INTO t_employee(employee_uid,login_name,login_pwd,actual_name,department_id) SELECT '{account}','{account}','{hashed}','W2 E2E',min(department_id) FROM t_department;",
            f"INSERT INTO t_role(role_name,role_code) VALUES ('W2 E2E','{account}');",
            f"INSERT INTO t_role_employee(role_id,employee_id) SELECT r.role_id,e.employee_id FROM t_role r,t_employee e WHERE r.role_code='{account}' AND e.login_name='{account}';",
            f"INSERT INTO t_role_menu(role_id,menu_id) SELECT r.role_id,m.menu_id FROM t_role r,t_menu m WHERE r.role_code='{account}' AND m.menu_id IN ({menus});"
        ]
    statements += ['COMMIT;']
elif sys.argv[1] == 'cleanup':
    statements = ['BEGIN; SET LOCAL search_path=xsy_v2;',
        f'DELETE FROM t_role_menu WHERE role_id IN (SELECT role_id FROM t_role WHERE role_code IN ({quoted}));',
        f'DELETE FROM t_role_employee WHERE employee_id IN (SELECT employee_id FROM t_employee WHERE login_name IN ({quoted}));',
        f'DELETE FROM t_role WHERE role_code IN ({quoted});',
        f'DELETE FROM t_employee WHERE login_name IN ({quoted});', 'COMMIT;']
else:
    raise ValueError('Expected setup or cleanup')

env = dict(os.environ, PGPASSWORD=os.environ['XSY_DB_PASSWORD'])
result = subprocess.run([r'D:\PostgreSQL\bin\psql.exe','-X','-q','-v','ON_ERROR_STOP=1','-h','127.0.0.1','-p','15432','-U','xsy_scm_app','-d','xsy_scm'],
    input='\n'.join(statements), text=True, capture_output=True, env=env)
if result.returncode:
    raise SystemExit('Fixture operation failed; credentials and SQL output withheld')
print('W2 E2E fixture ' + sys.argv[1] + ' complete')
