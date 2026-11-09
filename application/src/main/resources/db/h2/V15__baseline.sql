CREATE TABLE
    artifacts (
        embedded BOOLEAN,
        created TIMESTAMP(6),
        updated TIMESTAMP(6),
        metadata bytea,
        spec bytea,
        status bytea,
        created_by VARCHAR(255),
        id VARCHAR(255) NOT NULL,
        kind VARCHAR(255) NOT NULL,
        labels VARCHAR(255),
        name VARCHAR(255) NOT NULL,
        project VARCHAR(255) NOT NULL,
        state VARCHAR(255),
        updated_by VARCHAR(255),
        PRIMARY key (id)
    );

CREATE TABLE
    container_images (
        created TIMESTAMP(6),
        updated TIMESTAMP(6),
        metadata bytea,
        spec bytea,
        status bytea,
        created_by VARCHAR(255),
        id VARCHAR(255) NOT NULL,
        image VARCHAR(255) NOT NULL,
        kind VARCHAR(255) NOT NULL,
        labels VARCHAR(255),
        name VARCHAR(255) NOT NULL,
        project VARCHAR(255) NOT NULL,
        state VARCHAR(255),
        updated_by VARCHAR(255),
        PRIMARY key (id)
    );

CREATE TABLE
    dataitems (
        embedded BOOLEAN,
        created TIMESTAMP(6),
        updated TIMESTAMP(6),
        metadata bytea,
        spec bytea,
        status bytea,
        created_by VARCHAR(255),
        id VARCHAR(255) NOT NULL,
        kind VARCHAR(255) NOT NULL,
        labels VARCHAR(255),
        name VARCHAR(255) NOT NULL,
        project VARCHAR(255) NOT NULL,
        state VARCHAR(255),
        updated_by VARCHAR(255),
        PRIMARY key (id)
    );

CREATE TABLE
    extensions (
        created TIMESTAMP(6),
        updated TIMESTAMP(6),
        spec bytea NOT NULL,
        created_by VARCHAR(255),
        entity VARCHAR(255) NOT NULL,
        id VARCHAR(255) NOT NULL,
        kind VARCHAR(255) NOT NULL,
        name VARCHAR(255) NOT NULL,
        parent VARCHAR(255) NOT NULL,
        project VARCHAR(255) NOT NULL,
        updated_by VARCHAR(255),
        PRIMARY key (id)
    );

CREATE TABLE
    extensions_definitions (
        created TIMESTAMP(6),
        updated TIMESTAMP(6),
        metadata bytea,
        spec bytea,
        status bytea,
        created_by VARCHAR(255),
        id VARCHAR(255) NOT NULL,
        kind VARCHAR(255) NOT NULL,
        project VARCHAR(255) NOT NULL,
        state VARCHAR(255),
        updated_by VARCHAR(255),
        PRIMARY key (id)
    );

CREATE TABLE
    filesinfo (
        files bytea,
        entity_id VARCHAR(255) NOT NULL,
        entity_name VARCHAR(255) NOT NULL,
        id VARCHAR(255) NOT NULL,
        PRIMARY key (id),
        CONSTRAINT files_name_id_index UNIQUE (entity_name, entity_id)
    );

CREATE TABLE
    functions (
        embedded BOOLEAN,
        created TIMESTAMP(6),
        updated TIMESTAMP(6),
        metadata bytea,
        spec bytea,
        status bytea,
        created_by VARCHAR(255),
        id VARCHAR(255) NOT NULL,
        kind VARCHAR(255) NOT NULL,
        labels VARCHAR(255),
        name VARCHAR(255) NOT NULL,
        project VARCHAR(255) NOT NULL,
        state VARCHAR(255),
        updated_by VARCHAR(255),
        PRIMARY key (id)
    );

CREATE TABLE
    labels (
        id VARCHAR(255) NOT NULL,
        label VARCHAR(255) NOT NULL,
        project VARCHAR(255) NOT NULL,
        PRIMARY key (id),
        CONSTRAINT labels_prj_lbl_index UNIQUE (project, label)
    );

CREATE TABLE
    logs (
        kind VARCHAR(255) NOT NULL,
        created TIMESTAMP(6),
        updated TIMESTAMP(6),
        metadata bytea,
        spec bytea,
        status bytea,
        content bytea,
        created_by VARCHAR(255),
        id VARCHAR(255) NOT NULL,
        project VARCHAR(255) NOT NULL,
        run VARCHAR(255) NOT NULL,
        updated_by VARCHAR(255),
        PRIMARY key (id)
    );

CREATE TABLE
    metrics (
        data bytea,
        entity_id VARCHAR(255) NOT NULL,
        entity_name VARCHAR(255) NOT NULL,
        id VARCHAR(255) NOT NULL,
        name VARCHAR(255) NOT NULL,
        PRIMARY key (id)
    );

CREATE TABLE
    models (
        embedded BOOLEAN,
        created TIMESTAMP(6),
        updated TIMESTAMP(6),
        metadata bytea,
        spec bytea,
        status bytea,
        created_by VARCHAR(255),
        id VARCHAR(255) NOT NULL,
        kind VARCHAR(255) NOT NULL,
        labels VARCHAR(255),
        name VARCHAR(255) NOT NULL,
        project VARCHAR(255) NOT NULL,
        state VARCHAR(255),
        updated_by VARCHAR(255),
        PRIMARY key (id)
    );

CREATE TABLE
    projects (
        created TIMESTAMP(6),
        updated TIMESTAMP(6),
        metadata bytea,
        spec bytea,
        status bytea,
        created_by VARCHAR(255),
        id VARCHAR(255) NOT NULL,
        kind VARCHAR(255) NOT NULL,
        name VARCHAR(255) UNIQUE,
        source VARCHAR(255),
        state VARCHAR(255),
        updated_by VARCHAR(255),
        PRIMARY key (id)
    );

CREATE TABLE
    relationships (
        updated TIMESTAMP(6),
        dest_id VARCHAR(255) NOT NULL,
        dest_key VARCHAR(255) NOT NULL,
        dest_type VARCHAR(255) NOT NULL,
        id VARCHAR(255) NOT NULL,
        project VARCHAR(255) NOT NULL,
        relationship VARCHAR(255) NOT NULL,
        source_id VARCHAR(255) NOT NULL,
        source_key VARCHAR(255) NOT NULL,
        source_type VARCHAR(255) NOT NULL,
        PRIMARY key (id)
    );

CREATE TABLE
    resource_metrics (
        created TIMESTAMP(6),
        updated TIMESTAMP(6),
        metadata bytea,
        data bytea,
        created_by VARCHAR(255),
        id VARCHAR(255) NOT NULL,
        project VARCHAR(255) NOT NULL,
        run VARCHAR(255) NOT NULL,
        updated_by VARCHAR(255),
        PRIMARY key (id)
    );

CREATE TABLE
    resource_shares (
        expires_at TIMESTAMP(6),
        issued_at TIMESTAMP(6),
        entity_id VARCHAR(255) NOT NULL,
        entity_type VARCHAR(255) NOT NULL,
        id VARCHAR(255) NOT NULL,
        owner VARCHAR(255),
        project VARCHAR(255) NOT NULL,
        username VARCHAR(255) NOT NULL,
        PRIMARY key (id)
    );

CREATE TABLE
    runs (
        created TIMESTAMP(6),
        updated TIMESTAMP(6),
        metadata bytea,
        spec bytea,
        status bytea,
        created_by VARCHAR(255),
        FUNCTION VARCHAR(255),
        id VARCHAR(255) NOT NULL,
        kind VARCHAR(255) NOT NULL,
        name VARCHAR(255),
        project VARCHAR(255) NOT NULL,
        state VARCHAR(255),
        task VARCHAR(255) NOT NULL,
        updated_by VARCHAR(255),
        workflow VARCHAR(255),
        PRIMARY key (id)
    );

CREATE TABLE
    secrets (
        embedded BOOLEAN,
        created TIMESTAMP(6),
        updated TIMESTAMP(6),
        metadata bytea,
        spec bytea,
        status bytea,
        created_by VARCHAR(255),
        id VARCHAR(255) NOT NULL,
        kind VARCHAR(255) NOT NULL,
        name VARCHAR(255) NOT NULL,
        project VARCHAR(255) NOT NULL,
        state VARCHAR(255),
        updated_by VARCHAR(255),
        PRIMARY key (id),
        UNIQUE (project, name)
    );

CREATE TABLE
    tasks (
        created TIMESTAMP(6),
        updated TIMESTAMP(6),
        spec bytea,
        created_by VARCHAR(255),
        FUNCTION VARCHAR(255),
        id VARCHAR(255) NOT NULL,
        kind VARCHAR(255) NOT NULL,
        project VARCHAR(255) NOT NULL,
        updated_by VARCHAR(255),
        workflow VARCHAR(255),
        PRIMARY key (id),
        UNIQUE (FUNCTION, kind),
        UNIQUE (workflow, kind)
    );

CREATE TABLE
    triggers (
        created TIMESTAMP(6),
        updated TIMESTAMP(6),
        metadata bytea,
        spec bytea,
        status bytea,
        created_by VARCHAR(255),
        id VARCHAR(255) NOT NULL,
        kind VARCHAR(255) NOT NULL,
        name VARCHAR(255) NOT NULL,
        project VARCHAR(255) NOT NULL,
        state VARCHAR(255),
        task VARCHAR(255) NOT NULL,
        updated_by VARCHAR(255),
        PRIMARY key (id)
    );

CREATE TABLE
    workflows (
        embedded BOOLEAN,
        created TIMESTAMP(6),
        updated TIMESTAMP(6),
        metadata bytea,
        spec bytea,
        status bytea,
        created_by VARCHAR(255),
        id VARCHAR(255) NOT NULL,
        kind VARCHAR(255) NOT NULL,
        labels VARCHAR(255),
        name VARCHAR(255) NOT NULL,
        project VARCHAR(255) NOT NULL,
        state VARCHAR(255),
        updated_by VARCHAR(255),
        PRIMARY key (id)
    );

CREATE index labels_prj_index ON labels (project);

CREATE index metrics_type_id_index ON metrics (entity_name, entity_id);

CREATE index relationships_project_index ON relationships (project);

CREATE index relationships_source_index ON relationships (source_id);

CREATE index relationships_dest_index ON relationships (dest_id);

CREATE TABLE
    IF NOT EXISTS runnables (
        id VARCHAR(255) NOT NULL PRIMARY KEY,
        _user VARCHAR(255),
        created TIMESTAMP,
        updated TIMESTAMP,
        _clazz VARCHAR(255),
        _data BINARY LARGE OBJECT
    );

CREATE INDEX IF NOT EXISTS runnables_id_index ON runnables (id, _clazz);

CREATE TABLE
    IF NOT EXISTS trigger_jobs (
        id VARCHAR(255) NOT NULL PRIMARY KEY,
        _user VARCHAR(255),
        created TIMESTAMP,
        updated TIMESTAMP,
        _clazz VARCHAR(255),
        _data BINARY LARGE OBJECT
    );

CREATE INDEX IF NOT EXISTS trigger_jobs_id_index ON trigger_jobs (id, _clazz);

CREATE TABLE
    IF NOT EXISTS refresh_tokens (
        id VARCHAR(255) NOT NULL PRIMARY KEY,
        _user VARCHAR(255),
        issued_at TIMESTAMP,
        expires_at TIMESTAMP,
        token VARCHAR(255) NOT NULL UNIQUE,
        SCOPE VARCHAR(255),
        ip_addr VARCHAR(255),
        _auth BINARY LARGE OBJECT
    );

CREATE INDEX IF NOT EXISTS refresh_tokens_token_idx ON refresh_tokens (token);

CREATE INDEX IF NOT EXISTS refresh_tokens_exp_idx ON refresh_tokens (expires_at);

CREATE INDEX IF NOT EXISTS refresh_tokens_user_idx ON refresh_tokens (_user);

CREATE TABLE
    IF NOT EXISTS personal_access_tokens (
        id VARCHAR(255) NOT NULL PRIMARY KEY,
        name VARCHAR(255),
        _user VARCHAR(255),
        issued_at TIMESTAMP,
        expires_at TIMESTAMP,
        token VARCHAR(255) NOT NULL UNIQUE,
        SCOPE VARCHAR(255),
        ip_addr VARCHAR(255),
        _auth BINARY LARGE OBJECT
    );

CREATE INDEX IF NOT EXISTS personal_access_tokens_token_idx ON personal_access_tokens (token);

CREATE INDEX IF NOT EXISTS personal_access_tokens_user_idx ON personal_access_tokens (_user);

-- Quartz schema
CREATE TABLE
    IF NOT EXISTS QRTZ_CALENDARS (SCHED_NAME VARCHAR(120) NOT NULL, CALENDAR_NAME VARCHAR(200) NOT NULL, CALENDAR IMAGE NOT NULL);

CREATE TABLE
    IF NOT EXISTS QRTZ_CRON_TRIGGERS (
        SCHED_NAME VARCHAR(120) NOT NULL,
        TRIGGER_NAME VARCHAR(200) NOT NULL,
        TRIGGER_GROUP VARCHAR(200) NOT NULL,
        CRON_EXPRESSION VARCHAR(120) NOT NULL,
        TIME_ZONE_ID VARCHAR(80)
    );

CREATE TABLE
    IF NOT EXISTS QRTZ_FIRED_TRIGGERS (
        SCHED_NAME VARCHAR(120) NOT NULL,
        ENTRY_ID VARCHAR(95) NOT NULL,
        TRIGGER_NAME VARCHAR(200) NOT NULL,
        TRIGGER_GROUP VARCHAR(200) NOT NULL,
        INSTANCE_NAME VARCHAR(200) NOT NULL,
        FIRED_TIME BIGINT NOT NULL,
        SCHED_TIME BIGINT NOT NULL,
        PRIORITY INTEGER NOT NULL,
        STATE VARCHAR(16) NOT NULL,
        JOB_NAME VARCHAR(200) NULL,
        JOB_GROUP VARCHAR(200) NULL,
        IS_NONCONCURRENT BOOLEAN NULL,
        REQUESTS_RECOVERY BOOLEAN NULL
    );

CREATE TABLE
    IF NOT EXISTS QRTZ_PAUSED_TRIGGER_GRPS (SCHED_NAME VARCHAR(120) NOT NULL, TRIGGER_GROUP VARCHAR(200) NOT NULL);

CREATE TABLE
    IF NOT EXISTS QRTZ_SCHEDULER_STATE (
        SCHED_NAME VARCHAR(120) NOT NULL,
        INSTANCE_NAME VARCHAR(200) NOT NULL,
        LAST_CHECKIN_TIME BIGINT NOT NULL,
        CHECKIN_INTERVAL BIGINT NOT NULL
    );

CREATE TABLE
    IF NOT EXISTS QRTZ_LOCKS (SCHED_NAME VARCHAR(120) NOT NULL, LOCK_NAME VARCHAR(40) NOT NULL);

CREATE TABLE
    IF NOT EXISTS QRTZ_JOB_DETAILS (
        SCHED_NAME VARCHAR(120) NOT NULL,
        JOB_NAME VARCHAR(200) NOT NULL,
        JOB_GROUP VARCHAR(200) NOT NULL,
        DESCRIPTION VARCHAR(250) NULL,
        JOB_CLASS_NAME VARCHAR(250) NOT NULL,
        IS_DURABLE BOOLEAN NOT NULL,
        IS_NONCONCURRENT BOOLEAN NOT NULL,
        IS_UPDATE_DATA BOOLEAN NOT NULL,
        REQUESTS_RECOVERY BOOLEAN NOT NULL,
        JOB_DATA IMAGE NULL
    );

CREATE TABLE
    IF NOT EXISTS QRTZ_SIMPLE_TRIGGERS (
        SCHED_NAME VARCHAR(120) NOT NULL,
        TRIGGER_NAME VARCHAR(200) NOT NULL,
        TRIGGER_GROUP VARCHAR(200) NOT NULL,
        REPEAT_COUNT BIGINT NOT NULL,
        REPEAT_INTERVAL BIGINT NOT NULL,
        TIMES_TRIGGERED BIGINT NOT NULL
    );

CREATE TABLE
    IF NOT EXISTS QRTZ_SIMPROP_TRIGGERS (
        SCHED_NAME VARCHAR(120) NOT NULL,
        TRIGGER_NAME VARCHAR(200) NOT NULL,
        TRIGGER_GROUP VARCHAR(200) NOT NULL,
        STR_PROP_1 VARCHAR(512) NULL,
        STR_PROP_2 VARCHAR(512) NULL,
        STR_PROP_3 VARCHAR(512) NULL,
        INT_PROP_1 INTEGER NULL,
        INT_PROP_2 INTEGER NULL,
        LONG_PROP_1 BIGINT NULL,
        LONG_PROP_2 BIGINT NULL,
        DEC_PROP_1 NUMERIC(13, 4) NULL,
        DEC_PROP_2 NUMERIC(13, 4) NULL,
        BOOL_PROP_1 BOOLEAN NULL,
        BOOL_PROP_2 BOOLEAN NULL
    );

CREATE TABLE
    IF NOT EXISTS QRTZ_BLOB_TRIGGERS (
        SCHED_NAME VARCHAR(120) NOT NULL,
        TRIGGER_NAME VARCHAR(200) NOT NULL,
        TRIGGER_GROUP VARCHAR(200) NOT NULL,
        BLOB_DATA IMAGE NULL
    );

CREATE TABLE
    IF NOT EXISTS QRTZ_TRIGGERS (
        SCHED_NAME VARCHAR(120) NOT NULL,
        TRIGGER_NAME VARCHAR(200) NOT NULL,
        TRIGGER_GROUP VARCHAR(200) NOT NULL,
        JOB_NAME VARCHAR(200) NOT NULL,
        JOB_GROUP VARCHAR(200) NOT NULL,
        DESCRIPTION VARCHAR(250) NULL,
        NEXT_FIRE_TIME BIGINT NULL,
        PREV_FIRE_TIME BIGINT NULL,
        PRIORITY INTEGER NULL,
        TRIGGER_STATE VARCHAR(16) NOT NULL,
        TRIGGER_TYPE VARCHAR(8) NOT NULL,
        START_TIME BIGINT NOT NULL,
        END_TIME BIGINT NULL,
        CALENDAR_NAME VARCHAR(200) NULL,
        MISFIRE_INSTR SMALLINT NULL,
        JOB_DATA IMAGE NULL
    );

ALTER TABLE QRTZ_CALENDARS ADD CONSTRAINT IF NOT EXISTS PK_QRTZ_CALENDARS PRIMARY KEY (SCHED_NAME, CALENDAR_NAME);

ALTER TABLE QRTZ_CRON_TRIGGERS ADD CONSTRAINT IF NOT EXISTS PK_QRTZ_CRON_TRIGGERS PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP);

ALTER TABLE QRTZ_FIRED_TRIGGERS ADD CONSTRAINT IF NOT EXISTS PK_QRTZ_FIRED_TRIGGERS PRIMARY KEY (SCHED_NAME, ENTRY_ID);

ALTER TABLE QRTZ_PAUSED_TRIGGER_GRPS ADD CONSTRAINT IF NOT EXISTS PK_QRTZ_PAUSED_TRIGGER_GRPS PRIMARY KEY (SCHED_NAME, TRIGGER_GROUP);

ALTER TABLE QRTZ_SCHEDULER_STATE ADD CONSTRAINT IF NOT EXISTS PK_QRTZ_SCHEDULER_STATE PRIMARY KEY (SCHED_NAME, INSTANCE_NAME);

ALTER TABLE QRTZ_LOCKS ADD CONSTRAINT IF NOT EXISTS PK_QRTZ_LOCKS PRIMARY KEY (SCHED_NAME, LOCK_NAME);

ALTER TABLE QRTZ_JOB_DETAILS ADD CONSTRAINT IF NOT EXISTS PK_QRTZ_JOB_DETAILS PRIMARY KEY (SCHED_NAME, JOB_NAME, JOB_GROUP);

ALTER TABLE QRTZ_SIMPLE_TRIGGERS ADD CONSTRAINT IF NOT EXISTS PK_QRTZ_SIMPLE_TRIGGERS PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP);

ALTER TABLE QRTZ_SIMPROP_TRIGGERS ADD CONSTRAINT IF NOT EXISTS PK_QRTZ_SIMPROP_TRIGGERS PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP);

ALTER TABLE QRTZ_TRIGGERS ADD CONSTRAINT IF NOT EXISTS PK_QRTZ_TRIGGERS PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP);

ALTER TABLE QRTZ_CRON_TRIGGERS ADD CONSTRAINT IF NOT EXISTS FK_QRTZ_CRON_TRIGGERS_QRTZ_TRIGGERS FOREIGN KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP) REFERENCES QRTZ_TRIGGERS (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP) ON DELETE CASCADE;

ALTER TABLE QRTZ_SIMPLE_TRIGGERS ADD CONSTRAINT IF NOT EXISTS FK_QRTZ_SIMPLE_TRIGGERS_QRTZ_TRIGGERS FOREIGN KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP) REFERENCES QRTZ_TRIGGERS (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP) ON DELETE CASCADE;

ALTER TABLE QRTZ_SIMPROP_TRIGGERS ADD CONSTRAINT IF NOT EXISTS FK_QRTZ_SIMPROP_TRIGGERS_QRTZ_TRIGGERS FOREIGN KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP) REFERENCES QRTZ_TRIGGERS (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP) ON DELETE CASCADE;

ALTER TABLE QRTZ_TRIGGERS ADD CONSTRAINT IF NOT EXISTS FK_QRTZ_TRIGGERS_QRTZ_JOB_DETAILS FOREIGN KEY (SCHED_NAME, JOB_NAME, JOB_GROUP) REFERENCES QRTZ_JOB_DETAILS (SCHED_NAME, JOB_NAME, JOB_GROUP);