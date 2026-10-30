# MedFlow PostgreSQL role scope and credential recovery

Audit and reset completed October 30, 2026, on the PostgreSQL cluster at localhost:5432.

The exact roles reviewed were `medflow_auth`, `medflow_patient`, `medflow_doctor`, `medflow_appointment`, `medflow_medical_records`, `medflow_lab`, and `medflow_prescription`.

Each owns only the database of the same name. None has superuser, database creation, role creation, replication, or row-security bypass privileges. No memberships involving these roles were found. Cluster-wide dependency inspection found only matching database ownership, with no references in unrelated databases. Per-database object checks found no additional dependencies before service startup. No active sessions used these roles at audit time.

Configuration-reference searches across the inspected Desktop and Documents project trees, including Payroll and password_vault, found these role names only in MedFlow-AI. No other dependent application was identified in this local audit. The cluster's eight pre-existing non-template databases for other projects remained owned by postgres and were not changed by the reset workflow.

The catalog methodology uses PostgreSQL's shared role/object dependency records, described in the [PostgreSQL 14 pg_shdepend documentation](https://www.postgresql.org/docs/14/catalog-pg-shdepend.html). The detailed audit, including local object/session metadata, remains in ignored `local-runtime/role-scope-audit.json`.

After reviewing the audit, the user's conditional authorization was applied to exactly these seven role passwords. Seven independent 48-byte cryptographic random values were encoded as 64-character passwords. SCRAM verifiers were computed locally and supplied in a single password-only ALTER ROLE transaction; role privileges, memberships, ownership, and administrator credentials were unchanged. The operation did not drop databases, tables, or records. Passwords are stored only in Windows DPAPI-encrypted local configuration/recovery files, excluded from Git; the PostgreSQL administrator password was held temporarily in memory and not saved. All seven new credentials passed database SELECT checks.

Services subsequently started using their approved development databases; their existing Hibernate update configuration created the application's schemas as needed. Gateway/API smoke tests passed with synthetic data and unchanged authentication/authorization.

An independent Docker application, `securevault-backend`, occupies localhost:8085 and uses its own PostgreSQL container exposed on 5433. Neither container was stopped or reconfigured. MedFlow Medical Records instead uses local configuration port 18085 and is discovered through Eureka. Other MedFlow ports retain their defaults.

This review establishes the observed local scope from catalog dependencies, sessions, and inspected configuration references. It is not an inventory of unrecorded clients on other computers.
