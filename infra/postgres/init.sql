-- API Sentinel — PostgreSQL Database Initialization Script
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Ensure timezone is strictly UTC
ALTER DATABASE api_sentinel SET timezone TO 'UTC';
