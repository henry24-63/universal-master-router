-- Migration: 0003_add_provisioning_jobs.sql
-- Description: Add provisioning_jobs and provisioning_resources tables to track automated resource provisioning.

CREATE TABLE IF NOT EXISTS provisioning_jobs (
  id TEXT PRIMARY KEY,
  connected_account_id TEXT NOT NULL,
  appwrite_project_id TEXT NOT NULL,
  template_id TEXT NOT NULL,
  template_version TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'PENDING', -- PENDING, IN_PROGRESS, COMPLETED, FAILED, PARTIAL, CANCELLED
  result_data TEXT, -- JSON representation of final status/summary
  error_message TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS provisioning_resources (
  id TEXT PRIMARY KEY,
  job_id TEXT NOT NULL,
  resource_type TEXT NOT NULL, -- DATABASE, TABLE, COLUMN, INDEX, LEGACY_COLLECTION
  resource_name TEXT NOT NULL,
  appwrite_id TEXT, -- The actual ID returned by Appwrite
  status TEXT NOT NULL DEFAULT 'PENDING',
  error_message TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (job_id) REFERENCES provisioning_jobs(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_provisioning_jobs_account ON provisioning_jobs(connected_account_id);
CREATE INDEX IF NOT EXISTS idx_provisioning_jobs_status ON provisioning_jobs(status);
CREATE INDEX IF NOT EXISTS idx_provisioning_resources_job ON provisioning_resources(job_id);
