"""Create and remove isolated W4 browser test accounts.

Credentials are accepted only through the process environment and are never
printed.  The fixture grants W1/W2 read capabilities needed to create product
and customer data, plus the W4 pricing permissions.
"""
from __future__ import annotations

import os
import re
import subprocess
import sys
from argon2 import PasswordHasher

name = os.environ["W4_E2E_NAME"]
password = os.environ["W4_E2E_PASSWORD"]
if not re.fullmatch(r"w4_e2e_[a-z0-9_]{1,15}", name):
    raise ValueError("Unexpected fixture account name")
accounts = [name, name + "_read"]
quoted = ",".join("'" + value + "'" for value in accounts)
full = ",".join(str(value) for value in [
    401, 402, 403, 404, 411, 412, 413, 414, 415, 416, 421, 422, 423, 424,
    431, 432, 433, 434, 441, 442, 443, 444, 445, 451, 452, 453, 454,
    461, 462, 463, 464, 471, 472, 473, 474, 475, 481, 482,
    425, 435, 486, 487, 501, 502, 503, 504, 505, 506,
    511, 512, 513, 514, 521, 522, 523, 524, 525, 531, 541,
    601,602,603,604,605,611,612,613,614,615,616,617,618,619,621,622,623,624,625,631,632,641,
])
read = ",".join(str(value) for value in [
    401, 402, 421, 431, 432, 433, 441, 451, 461, 462, 471,
    425, 435, 486, 501, 502, 503, 505, 506, 511, 521, 531, 541,
    601,602,603,604,605,611,621,631,641,
])

if len(sys.argv) != 2 or sys.argv[1] not in {"setup", "cleanup"}:
    raise ValueError("Expected setup or cleanup")

if sys.argv[1] == "setup":
    statements = ["BEGIN; SET LOCAL search_path=xsy_v2;"]
    for account, menus in [(accounts[0], full), (accounts[1], read)]:
        salted = password + "_" + account.upper() + "_" + account.lower()
        hashed = PasswordHasher(time_cost=2, memory_cost=16384,
                                parallelism=1, hash_len=32, salt_len=16).hash(salted)
        statements.extend([
            f"INSERT INTO t_employee(employee_uid,login_name,login_pwd,actual_name,department_id) SELECT '{account}','{account}','{hashed}','W4 E2E',min(department_id) FROM t_department;",
            f"INSERT INTO t_role(role_name,role_code) VALUES ('W4 E2E','{account}');",
            f"INSERT INTO t_role_employee(role_id,employee_id) SELECT r.role_id,e.employee_id FROM t_role r,t_employee e WHERE r.role_code='{account}' AND e.login_name='{account}';",
            f"INSERT INTO t_role_menu(role_id,menu_id) SELECT r.role_id,m.menu_id FROM t_role r,t_menu m WHERE r.role_code='{account}' AND m.menu_id IN ({menus});",
        ])
    statements.append("COMMIT;")
else:
    statements = ["BEGIN; SET LOCAL search_path=xsy_v2;"]
    statements.extend([
        f"DELETE FROM t_role_menu WHERE role_id IN (SELECT role_id FROM t_role WHERE role_code IN ({quoted}));",
        f"DELETE FROM t_role_employee WHERE employee_id IN (SELECT employee_id FROM t_employee WHERE login_name IN ({quoted}));",
        f"DELETE FROM t_role WHERE role_code IN ({quoted});",
        f"DELETE FROM t_employee WHERE login_name IN ({quoted});",
        "COMMIT;",
    ])

env = dict(os.environ, PGPASSWORD=os.environ.get("XSY_DB_PASSWORD", ""))
result = subprocess.run([
    r"D:\PostgreSQL\bin\psql.exe", "-X", "-q", "-v", "ON_ERROR_STOP=1",
    "-h", "127.0.0.1", "-p", "15432", "-U", os.environ.get("XSY_V2_DB_USERNAME", "xsy_scm_app"),
    "-d", "xsy_scm",
], input="\n".join(statements), text=True, capture_output=True, env=env)
if result.returncode:
    raise SystemExit("Fixture operation failed; credentials and SQL output withheld")
print("W4 E2E fixture " + sys.argv[1] + " complete")
