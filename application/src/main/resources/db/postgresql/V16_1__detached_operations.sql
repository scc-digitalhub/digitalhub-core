CREATE TABLE
    IF NOT EXISTS detached_operations (
        entity_type VARCHAR(255) NOT NULL,
        entity_id VARCHAR(255) NOT NULL,
        state VARCHAR(255) NOT NULL,
        event VARCHAR(255),
        started_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
        last_attempt TIMESTAMP(6) WITH TIME ZONE,
        attempts INTEGER NOT NULL DEFAULT 0,
        PRIMARY KEY (entity_type, entity_id, state)
    );
