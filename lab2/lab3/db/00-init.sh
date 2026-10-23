#!/bin/sh
set -eu
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -v app_password="$DB_PASSWORD" <<'SQL'
CREATE ROLE auction_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT PASSWORD :'app_password';
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO auction_app;
SQL
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -f /schema/01-schema.sql
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -f /schema/02-demo.sql
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'SQL'
GRANT SELECT, INSERT, UPDATE, DELETE ON app_users, collectible_items, auction_lots TO auction_app;
GRANT SELECT, INSERT, UPDATE ON auction_deals TO auction_app;
GRANT SELECT, INSERT ON bids TO auction_app;
GRANT SELECT, UPDATE ON auction_mutex TO auction_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO auction_app;
REVOKE ALL ON DATABASE auction FROM PUBLIC;
GRANT CONNECT ON DATABASE auction TO auction_app;
SQL
