"""Create / remove the temporary SmartAdmin feature-probe account.

Mirrors tools/w{1,2,3}_e2e_accounts.py: the password is salted exactly like
EmployeeService.generateSaltPassword (password + "_" + UID.upper() + "_" + UID.lower())
and hashed with the Argon2 parameters of
Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()
(t=2, m=16384, p=1, hashLen=32, saltLen=16).

The account carries administrator_flag = TRUE, so SmartAdmin's menu /
permission checks short-circuit and every native feature becomes reachable.
Credentials are taken from the environment and never printed.

Usage:  python smartadmin_probe_account.py setup|cleanup
"""
from __future__ import annotations

import os
import re
import subprocess
import sys
from argon2 import PasswordHasher

name = os.environ["SA_PROBE_NAME"]
password = os.environ["SA_PROBE_PASSWORD"]
if not re.fullmatch(r"sa_audit_[a-z0-9]{1,20}", name):
    raise ValueError("Unexpected probe account name")

PSQL = os.environ.get("PSQL_BIN", r"D:\PostgreSQL\bin\psql.exe")

if len(sys.argv) != 2 or sys.argv[1] not in {"setup", "cleanup"}:
    raise ValueError("Expected setup or cleanup")

if sys.argv[1] == "setup":
    uid = os.urandom(16).hex()
    salted = password + "_" + uid.upper() + "_" + uid.lower()
    hashed = PasswordHasher(time_cost=2, memory_cost=16384,
                            parallelism=1, hash_len=32, salt_len=16).hash(salted)
    statements = [
        "BEGIN; SET LOCAL search_path=xsy_v2;",
        "INSERT INTO t_employee(employee_uid,login_name,login_pwd,actual_name,"
        "department_id,administrator_flag,disabled_flag,deleted_flag) "
        f"SELECT '{uid}','{name}','{hashed}','SA Audit',min(department_id),"
        "TRUE,FALSE,FALSE FROM t_department;",
        "COMMIT;",
    ]
else:
    statements = [
        "BEGIN; SET LOCAL search_path=xsy_v2;",
        "DELETE FROM t_operate_log WHERE operate_user_name = "
        f"'{name}';",
        "DELETE FROM t_role_employee WHERE employee_id IN "
        f"(SELECT employee_id FROM t_employee WHERE login_name='{name}');",
        f"DELETE FROM t_employee WHERE login_name='{name}';",
        "COMMIT;",
    ]

env = dict(os.environ, PGPASSWORD=os.environ.get("XSY_DB_PASSWORD", ""))
result = subprocess.run([
    PSQL, "-X", "-q", "-v", "ON_ERROR_STOP=1",
    "-h", "127.0.0.1", "-p", "15432",
    "-U", os.environ.get("XSY_V2_DB_USERNAME", "xsy_scm_app"),
    "-d", os.environ.get("XSY_DB_NAME", "xsy_scm"),
], input="\n".join(statements), text=True, capture_output=True, env=env)
if result.returncode:
    raise SystemExit("Probe account fixture failed; SQL output withheld")
print("SmartAdmin probe account " + sys.argv[1] + " complete")
