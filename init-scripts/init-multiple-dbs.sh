#!/bin/bash
set -e

function create_db_if_not_exists() {
    local db=$1
    echo "Creating database '$db' if not exists..."
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "postgres" <<-EOSQL
        SELECT 'CREATE DATABASE $db'
        WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '$db')\gexec
EOSQL
}

create_db_if_not_exists "userdb"
create_db_if_not_exists "orderdb"
create_db_if_not_exists "payment_db"
