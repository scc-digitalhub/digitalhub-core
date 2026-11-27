CREATE TABLE
    IF NOT EXISTS detached_operations (
        entity_type VARCHAR(255) NOT NULL,
        entity_id VARCHAR(255) NOT NULL,
        state VARCHAR(255) NOT NULL,
        event VARCHAR(255),
        started_at TIMESTAMP NOT NULL,
        last_attempt TIMESTAMP,
        attempts INTEGER NOT NULL DEFAULT 0,
        PRIMARY KEY (entity_type, entity_id, state)
    );
