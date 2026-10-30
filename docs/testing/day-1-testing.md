# Day-1 testing

Auth integration tests cover successful and invalid registration, duplicate email, public role mass-assignment, login success/failure, and protected `/me`. Tests use H2 only as an isolated PostgreSQL-compatible test fixture. Production uses PostgreSQL.
