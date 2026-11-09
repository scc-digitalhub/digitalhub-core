-- ADD explicit entity key to all tables
ALTER TABLE artifacts
ADD COLUMN IF NOT EXISTS entity_key VARCHAR(255);

ALTER TABLE container_images
ADD COLUMN IF NOT EXISTS entity_key VARCHAR(255);

ALTER TABLE dataitems
ADD COLUMN IF NOT EXISTS entity_key VARCHAR(255);

ALTER TABLE extensions
ADD COLUMN IF NOT EXISTS entity_key VARCHAR(255);

ALTER TABLE extensions_definitions
ADD COLUMN IF NOT EXISTS entity_key VARCHAR(255);

ALTER TABLE functions
ADD COLUMN IF NOT EXISTS entity_key VARCHAR(255);

ALTER TABLE models
ADD COLUMN IF NOT EXISTS entity_key VARCHAR(255);

ALTER TABLE runs
ADD COLUMN IF NOT EXISTS entity_key VARCHAR(255);

ALTER TABLE secrets
ADD COLUMN IF NOT EXISTS entity_key VARCHAR(255);

ALTER TABLE tasks
ADD COLUMN IF NOT EXISTS entity_key VARCHAR(255);

ALTER TABLE triggers
ADD COLUMN IF NOT EXISTS entity_key VARCHAR(255);

ALTER TABLE workflows
ADD COLUMN IF NOT EXISTS entity_key VARCHAR(255);

-- Generate all entity keys for existing records where entity_key is NULL
UPDATE artifacts
SET
    entity_key=concat ('store://', project, '/artifact/', kind, '/', name, ':', id)
WHERE
    entity_key IS NULL;

UPDATE container_images
SET
    entity_key=concat ('store://', project, '/containerimage/', kind, '/', name, ':', id)
WHERE
    entity_key IS NULL;

UPDATE dataitems
SET
    entity_key=concat ('store://', project, '/dataitem/', kind, '/', name, ':', id)
WHERE
    entity_key IS NULL;

UPDATE extensions
SET
    entity_key=concat ('store://', project, '/extension/', kind, '/', name, ':', id)
WHERE
    entity_key IS NULL;

UPDATE extensions_definitions
SET
    entity_key=concat ('store://', project, '/extensionsdefinition/', kind, '/', id, ':', id)
WHERE
    entity_key IS NULL;

UPDATE functions
SET
    entity_key=concat ('store://', project, '/function/', kind, '/', name, ':', id)
WHERE
    entity_key IS NULL;

UPDATE models
SET
    entity_key=concat ('store://', project, '/model/', kind, '/', name, ':', id)
WHERE
    entity_key IS NULL;

UPDATE runs
SET
    entity_key=concat ('store://', project, '/run/', kind, '/', name, ':', id)
WHERE
    entity_key IS NULL;

UPDATE secrets
SET
    entity_key=concat ('store://', project, '/secret/', kind, '/', name, ':', id)
WHERE
    entity_key IS NULL;

UPDATE tasks
SET
    entity_key=concat ('store://', project, '/task/', kind, '/', id, ':', id)
WHERE
    entity_key IS NULL;

UPDATE triggers
SET
    entity_key=concat ('store://', project, '/trigger/', kind, '/', name, ':', id)
WHERE
    entity_key IS NULL;

UPDATE workflows
SET
    entity_key=concat ('store://', project, '/workflow/', kind, '/', name, ':', id)
WHERE
    entity_key IS NULL;

-- Now add UNIQUE constraints FOR entity_key
ALTER TABLE artifacts ADD CONSTRAINT unique_entity_key_artifacts UNIQUE (entity_key);

ALTER TABLE container_images ADD CONSTRAINT unique_entity_key_container_images UNIQUE (entity_key);

ALTER TABLE dataitems ADD CONSTRAINT unique_entity_key_dataitems UNIQUE (entity_key);

ALTER TABLE extensions ADD CONSTRAINT unique_entity_key_extensions UNIQUE (entity_key);

ALTER TABLE extensions_definitions ADD CONSTRAINT unique_entity_key_extensions_definitions UNIQUE (entity_key);

ALTER TABLE functions ADD CONSTRAINT unique_entity_key_functions UNIQUE (entity_key);

ALTER TABLE models ADD CONSTRAINT unique_entity_key_models UNIQUE (entity_key);

ALTER TABLE runs ADD CONSTRAINT unique_entity_key_runs UNIQUE (entity_key);

ALTER TABLE secrets ADD CONSTRAINT unique_entity_key_secrets UNIQUE (entity_key);

ALTER TABLE tasks ADD CONSTRAINT unique_entity_key_tasks UNIQUE (entity_key);

ALTER TABLE triggers ADD CONSTRAINT unique_entity_key_triggers UNIQUE (entity_key);

ALTER TABLE workflows ADD CONSTRAINT unique_entity_key_workflows UNIQUE (entity_key);

-- update extensions definition
ALTER TABLE extensions
ADD COLUMN state VARCHAR(255);

ALTER TABLE extensions
ADD COLUMN status bytea;

CREATE index extensions_parent ON extensions (parent);

-- update logs definition
ALTER TABLE logs
DROP COLUMN if EXISTS kind;

ALTER TABLE logs
DROP COLUMN if EXISTS spec;

-- update triggers definition
ALTER TABLE triggers
ADD COLUMN FUNCTION VARCHAR(255);

ALTER TABLE triggers
ADD COLUMN workflow VARCHAR(255);

CREATE index triggers_function ON triggers (FUNCTION);

CREATE index triggers_workflow ON triggers (WORKFLOW);

-- add index for runs
CREATE index runs_function ON runs (FUNCTION);

CREATE index runs_workflow ON runs (WORKFLOW);

-- add index for metrics uniqueness
CREATE UNIQUE INDEX metrics_type_id_name_index ON metrics (entity_name, entity_id, name);

-- align tasks table
ALTER TABLE tasks
DROP COLUMN IF EXISTS metadata;

-- performance optimizations
CREATE INDEX artifacts_project_name_created_idx ON artifacts (project, name, created DESC);

CREATE INDEX artifacts_created_by_idx ON artifacts (created_by);

CREATE INDEX artifacts_project_created_by_idx ON artifacts (project, created_by);

CREATE INDEX artifacts_project_kind_idx ON artifacts (project, kind);

CREATE INDEX container_images_created_by_idx ON container_images (created_by);

CREATE INDEX container_images_project_created_by_idx ON container_images (project, created_by);

CREATE INDEX container_images_project_kind_idx ON container_images (project, kind);

CREATE INDEX dataitems_project_name_created_idx ON dataitems (project, name, created DESC);

CREATE INDEX dataitems_created_by_idx ON dataitems (created_by);

CREATE INDEX dataitems_project_created_by_idx ON dataitems (project, created_by);

CREATE INDEX dataitems_project_kind_idx ON dataitems (project, kind);

CREATE INDEX functions_project_name_created_idx ON functions (project, name, created DESC);

CREATE INDEX functions_created_by_idx ON functions (created_by);

CREATE INDEX functions_project_created_by_idx ON functions (project, created_by);

CREATE INDEX functions_project_kind_idx ON functions (project, kind);

CREATE INDEX models_project_name_created_idx ON models (project, name, created DESC);

CREATE INDEX models_created_by_idx ON models (created_by);

CREATE INDEX models_project_created_by_idx ON models (project, created_by);

CREATE INDEX models_project_kind_idx ON models (project, kind);

CREATE INDEX workflows_project_name_created_idx ON workflows (project, name, created DESC);

CREATE INDEX workflows_created_by_idx ON workflows (created_by);

CREATE INDEX workflows_project_created_by_idx ON workflows (project, created_by);

CREATE INDEX workflows_project_kind_idx ON workflows (project, kind);

CREATE INDEX runs_project_idx ON runs (project);

CREATE INDEX runs_task_idx ON runs (task);

CREATE INDEX runs_created_by_idx ON runs (created_by);

CREATE INDEX runs_project_created_by_idx ON runs (project, created_by);

CREATE INDEX runs_project_kind_idx ON runs (project, kind);

CREATE INDEX triggers_task_idx ON triggers (task);

CREATE INDEX triggers_created_by_idx ON triggers (created_by);

CREATE INDEX triggers_project_created_by_idx ON triggers (project, created_by);

CREATE INDEX triggers_project_kind_idx ON triggers (project, kind);

CREATE INDEX logs_project_idx ON logs (project);

CREATE INDEX logs_run_idx ON logs (run);

CREATE INDEX logs_created_by_idx ON logs (created_by);

CREATE INDEX resource_metrics_project_idx ON resource_metrics (project);

CREATE INDEX resource_metrics_run_idx ON resource_metrics (run);

CREATE INDEX resource_metrics_created_by_idx ON resource_metrics (created_by);

CREATE INDEX resource_shares_project_owner_idx ON resource_shares (project, owner);

CREATE INDEX resource_shares_owner_idx ON resource_shares (owner);

CREATE INDEX resource_shares_username_project_idx ON resource_shares (username, project);

CREATE INDEX resource_shares_project_entity_user_idx ON resource_shares (project, entity_type, entity_id, username);

CREATE INDEX runnables_clazz_idx ON runnables (_clazz);

CREATE INDEX trigger_jobs_clazz_idx ON trigger_jobs (_clazz);

CREATE INDEX extensions_created_by_idx ON extensions (created_by);

CREATE INDEX extensions_project_created_by_idx ON extensions (project, created_by);

CREATE INDEX extensions_project_kind_idx ON extensions (project, kind);

CREATE INDEX extensions_definitions_created_by_idx ON extensions_definitions (created_by);

CREATE INDEX extensions_definitions_project_created_by_idx ON extensions_definitions (project, created_by);

CREATE INDEX extensions_definitions_project_kind_idx ON extensions_definitions (project, kind);

CREATE INDEX secrets_created_by_idx ON secrets (created_by);

CREATE INDEX secrets_project_created_by_idx ON secrets (project, created_by);

CREATE INDEX secrets_project_kind_idx ON secrets (project, kind);

CREATE INDEX tasks_created_by_idx ON tasks (created_by);

CREATE INDEX tasks_project_created_by_idx ON tasks (project, created_by);

CREATE INDEX tasks_project_kind_idx ON tasks (project, kind);