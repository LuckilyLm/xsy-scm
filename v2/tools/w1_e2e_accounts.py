"""Provision/remove uniquely named local W1 test users. Credentials stay in process memory."""
import os
import re
import subprocess
import sys
from argon2 import PasswordHasher

name = os.environ['W1_E2E_NAME']
if not re.fullmatch(r'w1_e2e_[a-z0-9_]{1,15}', name):
    raise ValueError('Unexpected fixture account name')
names = [name, name + '_read']
quoted = ','.join("'" + value + "'" for value in names)
if sys.argv[1] == 'setup':
    statements = ['BEGIN; SET LOCAL search_path=xsy_v2;']
    for account, menus in [(names[0], '401,402,403,404,411,412,413,414,415,416,421,422,423,424'), (names[1], '401,402,403,404,411,421')]:
        # EmployeeService.generateSaltPassword then SecurityPasswordService's Argon2 v5_8 parameters.
        salted = os.environ['W1_E2E_PASSWORD'] + '_' + account.upper() + '_' + account.lower()
        hashed = PasswordHasher(time_cost=2, memory_cost=16384, parallelism=1, hash_len=32, salt_len=16).hash(salted)
        statements += [
            f"INSERT INTO t_employee(employee_uid,login_name,login_pwd,actual_name,department_id) SELECT '{account}','{account}','{hashed}','W1 E2E',min(department_id) FROM t_department;",
            f"INSERT INTO t_role(role_name,role_code) VALUES ('W1 E2E','{account}');",
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
print('W1 E2E fixture ' + sys.argv[1] + ' complete')
