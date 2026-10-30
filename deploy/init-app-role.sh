#!/bin/sh
set -eu
# Runs only when an isolated new container volume is initialized; never against native PostgreSQL.
case "$APP_DB_USER" in medflow_auth|medflow_patient|medflow_doctor|medflow_appointment|medflow_medical_records|medflow_lab|medflow_prescription|medflow_pharmacy|medflow_nutrition|medflow_billing|medflow_emergency|medflow_notification) ;; *) exit 1;; esac
[ "$POSTGRES_DB" = "$APP_DB_USER" ] || exit 1
password=$(cat "$APP_DB_PASSWORD_FILE")
# Quote SQL data safely. Nothing is echoed or passed as a command argument.
escaped=$(printf '%s' "$password" | sed "s/'/''/g")
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<SQL
SET log_statement='none';
SET standard_conforming_strings=on;
SET log_duration=off;
SET log_min_duration_statement=-1;
SET log_min_error_statement='panic';
CREATE ROLE $APP_DB_USER LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD '$escaped';
ALTER DATABASE $POSTGRES_DB OWNER TO $APP_DB_USER;
SQL
unset password escaped
