-- Creates separate databases for each Java microservice (Database-per-Service pattern)
-- Only services that require ACID guarantees use PostgreSQL

CREATE DATABASE orchestrator_db;
CREATE DATABASE account_db;
CREATE DATABASE ledger_db;
