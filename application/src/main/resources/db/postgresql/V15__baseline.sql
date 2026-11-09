CREATE TABLE
    public.artifacts (
        created TIMESTAMP(6) WITHOUT TIME zone,
        created_by CHARACTER VARYING(255),
        embedded BOOLEAN,
        id CHARACTER VARYING(255) NOT NULL,
        kind CHARACTER VARYING(255) NOT NULL,
        labels CHARACTER VARYING(255),
        metadata bytea,
        name CHARACTER VARYING(255) NOT NULL,
        project CHARACTER VARYING(255) NOT NULL,
        spec bytea,
        state CHARACTER VARYING(255),
        status bytea,
        updated TIMESTAMP(6) WITHOUT TIME zone,
        updated_by CHARACTER VARYING(255),
        PRIMARY key (id)
    );

CREATE TABLE
    public.container_images (
        created TIMESTAMP(6) WITHOUT TIME zone,
        created_by CHARACTER VARYING(255),
        id CHARACTER VARYING(255) NOT NULL,
        image CHARACTER VARYING(255) NOT NULL,
        kind CHARACTER VARYING(255) NOT NULL,
        labels CHARACTER VARYING(255),
        metadata bytea,
        name CHARACTER VARYING(255) NOT NULL,
        project CHARACTER VARYING(255) NOT NULL,
        spec bytea,
        state CHARACTER VARYING(255),
        status bytea,
        updated TIMESTAMP(6) WITHOUT TIME zone,
        updated_by CHARACTER VARYING(255),
        PRIMARY key (id)
    );

CREATE TABLE
    public.dataitems (
        created TIMESTAMP(6) WITHOUT TIME zone,
        created_by CHARACTER VARYING(255),
        embedded BOOLEAN,
        id CHARACTER VARYING(255) NOT NULL,
        kind CHARACTER VARYING(255) NOT NULL,
        labels CHARACTER VARYING(255),
        metadata bytea,
        name CHARACTER VARYING(255) NOT NULL,
        project CHARACTER VARYING(255) NOT NULL,
        spec bytea,
        state CHARACTER VARYING(255),
        status bytea,
        updated TIMESTAMP(6) WITHOUT TIME zone,
        updated_by CHARACTER VARYING(255),
        PRIMARY key (id)
    );

CREATE TABLE
    public.extensions (
        created TIMESTAMP(6) WITHOUT TIME zone,
        created_by CHARACTER VARYING(255),
        entity CHARACTER VARYING(255) NOT NULL,
        id CHARACTER VARYING(255) NOT NULL,
        kind CHARACTER VARYING(255) NOT NULL,
        name CHARACTER VARYING(255) NOT NULL,
        parent CHARACTER VARYING(255) NOT NULL,
        project CHARACTER VARYING(255) NOT NULL,
        spec bytea NOT NULL,
        updated TIMESTAMP(6) WITHOUT TIME zone,
        updated_by CHARACTER VARYING(255),
        PRIMARY key (id)
    );

CREATE TABLE
    public.extensions_definitions (
        created TIMESTAMP(6) WITHOUT TIME zone,
        created_by CHARACTER VARYING(255),
        id CHARACTER VARYING(255) NOT NULL,
        kind CHARACTER VARYING(255) NOT NULL,
        metadata bytea,
        project CHARACTER VARYING(255) NOT NULL,
        spec bytea,
        state CHARACTER VARYING(255),
        status bytea,
        updated TIMESTAMP(6) WITHOUT TIME zone,
        updated_by CHARACTER VARYING(255),
        PRIMARY key (id)
    );

CREATE TABLE
    public.filesinfo (
        entity_id CHARACTER VARYING(255) NOT NULL,
        entity_name CHARACTER VARYING(255) NOT NULL,
        files bytea,
        id CHARACTER VARYING(255) NOT NULL,
        PRIMARY key (id),
        CONSTRAINT files_name_id_index UNIQUE (entity_name, entity_id)
    );

CREATE TABLE
    public.functions (
        created TIMESTAMP(6) WITHOUT TIME zone,
        created_by CHARACTER VARYING(255),
        embedded BOOLEAN,
        id CHARACTER VARYING(255) NOT NULL,
        kind CHARACTER VARYING(255) NOT NULL,
        labels CHARACTER VARYING(255),
        metadata bytea,
        name CHARACTER VARYING(255) NOT NULL,
        project CHARACTER VARYING(255) NOT NULL,
        spec bytea,
        state CHARACTER VARYING(255),
        status bytea,
        updated TIMESTAMP(6) WITHOUT TIME zone,
        updated_by CHARACTER VARYING(255),
        PRIMARY key (id)
    );

CREATE TABLE
    public.labels (
        id CHARACTER VARYING(255) NOT NULL,
        label CHARACTER VARYING(255) NOT NULL,
        project CHARACTER VARYING(255) NOT NULL,
        PRIMARY key (id),
        CONSTRAINT labels_prj_lbl_index UNIQUE (project, label)
    );

CREATE TABLE
    public.logs (
        content bytea,
        created TIMESTAMP(6) WITHOUT TIME zone,
        created_by CHARACTER VARYING(255),
        id CHARACTER VARYING(255) NOT NULL,
        kind CHARACTER VARYING(255) NOT NULL,
        metadata bytea,
        project CHARACTER VARYING(255) NOT NULL,
        run CHARACTER VARYING(255) NOT NULL,
        spec bytea,
        status bytea,
        updated TIMESTAMP(6) WITHOUT TIME zone,
        updated_by CHARACTER VARYING(255),
        PRIMARY key (id)
    );

CREATE TABLE
    public.metrics (
        data bytea,
        entity_id CHARACTER VARYING(255) NOT NULL,
        entity_name CHARACTER VARYING(255) NOT NULL,
        id CHARACTER VARYING(255) NOT NULL,
        name CHARACTER VARYING(255) NOT NULL,
        PRIMARY key (id)
    );

CREATE TABLE
    public.models (
        created TIMESTAMP(6) WITHOUT TIME zone,
        created_by CHARACTER VARYING(255),
        embedded BOOLEAN,
        id CHARACTER VARYING(255) NOT NULL,
        kind CHARACTER VARYING(255) NOT NULL,
        labels CHARACTER VARYING(255),
        metadata bytea,
        name CHARACTER VARYING(255) NOT NULL,
        project CHARACTER VARYING(255) NOT NULL,
        spec bytea,
        state CHARACTER VARYING(255),
        status bytea,
        updated TIMESTAMP(6) WITHOUT TIME zone,
        updated_by CHARACTER VARYING(255),
        PRIMARY key (id)
    );

CREATE TABLE
    public.projects (
        created TIMESTAMP(6) WITHOUT TIME zone,
        created_by CHARACTER VARYING(255),
        id CHARACTER VARYING(255) NOT NULL,
        kind CHARACTER VARYING(255) NOT NULL,
        metadata bytea,
        name CHARACTER VARYING(255) NOT NULL UNIQUE,
        source CHARACTER VARYING(255),
        spec bytea,
        state CHARACTER VARYING(255),
        status bytea,
        updated TIMESTAMP(6) WITHOUT TIME zone,
        updated_by CHARACTER VARYING(255),
        PRIMARY key (id)
    );

CREATE TABLE
    public.relationships (
        dest_id CHARACTER VARYING(255) NOT NULL,
        dest_key CHARACTER VARYING(255) NOT NULL,
        dest_type CHARACTER VARYING(255) NOT NULL,
        id CHARACTER VARYING(255) NOT NULL,
        project CHARACTER VARYING(255) NOT NULL,
        relationship CHARACTER VARYING(255) NOT NULL,
        source_id CHARACTER VARYING(255) NOT NULL,
        source_key CHARACTER VARYING(255) NOT NULL,
        source_type CHARACTER VARYING(255) NOT NULL,
        updated TIMESTAMP(6) WITHOUT TIME zone,
        PRIMARY key (id)
    );

CREATE TABLE
    public.resource_metrics (
        created TIMESTAMP(6) WITHOUT TIME zone,
        created_by CHARACTER VARYING(255),
        data bytea,
        id CHARACTER VARYING(255) NOT NULL,
        metadata bytea,
        project CHARACTER VARYING(255) NOT NULL,
        run CHARACTER VARYING(255) NOT NULL,
        updated TIMESTAMP(6) WITHOUT TIME zone,
        updated_by CHARACTER VARYING(255),
        PRIMARY key (id)
    );

CREATE TABLE
    public.resource_shares (
        id CHARACTER VARYING(255) NOT NULL,
        entity_type CHARACTER VARYING(255) NOT NULL,
        entity_id CHARACTER VARYING(255) NOT NULL,
        expires_at TIMESTAMP(6) WITHOUT TIME zone,
        issued_at TIMESTAMP(6) WITHOUT TIME zone,
        owner CHARACTER VARYING(255),
        project CHARACTER VARYING(255) NOT NULL,
        username CHARACTER VARYING(255) NOT NULL,
        PRIMARY key (id)
    );

CREATE TABLE
    public.runs (
        created TIMESTAMP(6) WITHOUT TIME zone,
        created_by CHARACTER VARYING(255),
        FUNCTION CHARACTER VARYING(255),
        id CHARACTER VARYING(255) NOT NULL,
        kind CHARACTER VARYING(255) NOT NULL,
        metadata bytea,
        name CHARACTER VARYING(255),
        project CHARACTER VARYING(255) NOT NULL,
        spec bytea,
        state CHARACTER VARYING(255),
        status bytea,
        task CHARACTER VARYING(255) NOT NULL,
        updated TIMESTAMP(6) WITHOUT TIME zone,
        updated_by CHARACTER VARYING(255),
        workflow CHARACTER VARYING(255),
        PRIMARY key (id)
    );

CREATE TABLE
    public.secrets (
        created TIMESTAMP(6) WITHOUT TIME zone,
        created_by CHARACTER VARYING(255),
        embedded BOOLEAN,
        id CHARACTER VARYING(255) NOT NULL,
        kind CHARACTER VARYING(255) NOT NULL,
        metadata bytea,
        name CHARACTER VARYING(255) NOT NULL,
        project CHARACTER VARYING(255) NOT NULL,
        spec bytea,
        state CHARACTER VARYING(255),
        status bytea,
        updated TIMESTAMP(6) WITHOUT TIME zone,
        updated_by CHARACTER VARYING(255),
        PRIMARY key (id),
        UNIQUE (project, name)
    );

CREATE TABLE
    public.tasks (
        created TIMESTAMP(6) WITHOUT TIME zone,
        created_by CHARACTER VARYING(255),
        FUNCTION CHARACTER VARYING(255),
        id CHARACTER VARYING(255) NOT NULL,
        kind CHARACTER VARYING(255) NOT NULL,
        project CHARACTER VARYING(255) NOT NULL,
        spec bytea,
        updated TIMESTAMP(6) WITHOUT TIME zone,
        updated_by CHARACTER VARYING(255),
        workflow CHARACTER VARYING(255),
        PRIMARY key (id),
        UNIQUE (FUNCTION, kind),
        UNIQUE (workflow, kind)
    );

CREATE TABLE
    public.triggers (
        created TIMESTAMP(6) WITHOUT TIME zone,
        created_by CHARACTER VARYING(255),
        id CHARACTER VARYING(255) NOT NULL,
        kind CHARACTER VARYING(255) NOT NULL,
        metadata bytea,
        name CHARACTER VARYING(255) NOT NULL,
        project CHARACTER VARYING(255) NOT NULL,
        spec bytea,
        state CHARACTER VARYING(255),
        status bytea,
        task CHARACTER VARYING(255) NOT NULL,
        updated TIMESTAMP(6) WITHOUT TIME zone,
        updated_by CHARACTER VARYING(255),
        PRIMARY key (id)
    );

CREATE TABLE
    public.workflows (
        created TIMESTAMP(6) WITHOUT TIME zone,
        created_by CHARACTER VARYING(255),
        embedded BOOLEAN,
        id CHARACTER VARYING(255) NOT NULL,
        kind CHARACTER VARYING(255) NOT NULL,
        labels CHARACTER VARYING(255),
        metadata bytea,
        name CHARACTER VARYING(255) NOT NULL,
        project CHARACTER VARYING(255) NOT NULL,
        spec bytea,
        state CHARACTER VARYING(255),
        status bytea,
        updated TIMESTAMP(6) WITHOUT TIME zone,
        updated_by CHARACTER VARYING(255),
        PRIMARY key (id)
    );

CREATE INDEX labels_prj_index ON public.labels USING btree (project);

CREATE INDEX metrics_type_id_index ON public.metrics USING btree (entity_name, entity_id);

CREATE INDEX relationships_dest_index ON public.relationships USING btree (dest_id);

CREATE INDEX relationships_project_index ON public.relationships USING btree (project);

CREATE INDEX relationships_source_index ON public.relationships USING btree (source_id);

CREATE TABLE
    public.runnables (
        id VARCHAR(255) NOT NULL PRIMARY KEY,
        _user VARCHAR(255),
        created TIMESTAMP(6)
        WITH
            TIME ZONE,
            updated TIMESTAMP(6)
        WITH
            TIME ZONE,
            _clazz VARCHAR(255) NOT NULL,
            _data BYTEA
    );

CREATE INDEX runnables_id_index ON public.runnables (id, _clazz);

CREATE TABLE
    public.trigger_jobs (
        id VARCHAR(255) NOT NULL PRIMARY KEY,
        _user VARCHAR(255),
        created TIMESTAMP(6)
        WITH
            TIME ZONE,
            updated TIMESTAMP(6)
        WITH
            TIME ZONE,
            _clazz VARCHAR(255) NOT NULL,
            _data BYTEA
    );

CREATE INDEX trigger_jobs_id_index ON public.trigger_jobs (id, _clazz);

CREATE TABLE
    refresh_tokens (
        id VARCHAR(255) NOT NULL PRIMARY KEY,
        _user VARCHAR(255),
        issued_at TIMESTAMP(6)
        WITH
            TIME ZONE,
            expires_at TIMESTAMP(6)
        WITH
            TIME ZONE,
            token VARCHAR(255) NOT NULL UNIQUE,
            SCOPE VARCHAR(255),
            ip_addr VARCHAR(255),
            _auth BYTEA
    );

CREATE INDEX refresh_tokens_token_idx ON refresh_tokens (token);

CREATE INDEX refresh_tokens_exp_idx ON refresh_tokens (expires_at);

CREATE INDEX refresh_tokens_user_idx ON refresh_tokens (_user);

CREATE TABLE
    personal_access_tokens (
        id VARCHAR(255) NOT NULL PRIMARY KEY,
        name VARCHAR(255),
        _user VARCHAR(255),
        issued_at TIMESTAMP(6)
        WITH
            TIME ZONE,
            expires_at TIMESTAMP(6)
        WITH
            TIME ZONE,
            token VARCHAR(255) NOT NULL UNIQUE,
            SCOPE VARCHAR(255),
            ip_addr VARCHAR(255),
            _auth BYTEA
    );

CREATE INDEX personal_access_tokens_token_idx ON personal_access_tokens (token);

CREATE INDEX personal_access_tokens_user_idx ON personal_access_tokens (_user);

-- alter table public.runnable
--     owner to postgres;
CREATE TABLE
    public.QRTZ_JOB_DETAILS (
        SCHED_NAME VARCHAR(120) NOT NULL,
        JOB_NAME VARCHAR(200) NOT NULL,
        JOB_GROUP VARCHAR(200) NOT NULL,
        DESCRIPTION VARCHAR(250) NULL,
        JOB_CLASS_NAME VARCHAR(250) NOT NULL,
        IS_DURABLE BOOL NOT NULL,
        IS_NONCONCURRENT BOOL NOT NULL,
        IS_UPDATE_DATA BOOL NOT NULL,
        REQUESTS_RECOVERY BOOL NOT NULL,
        JOB_DATA BYTEA NULL,
        PRIMARY KEY (SCHED_NAME, JOB_NAME, JOB_GROUP)
    );

CREATE TABLE
    public.QRTZ_TRIGGERS (
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
        JOB_DATA BYTEA NULL,
        PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP),
        FOREIGN KEY (SCHED_NAME, JOB_NAME, JOB_GROUP) REFERENCES QRTZ_JOB_DETAILS (SCHED_NAME, JOB_NAME, JOB_GROUP)
    );

CREATE TABLE
    public.QRTZ_SIMPLE_TRIGGERS (
        SCHED_NAME VARCHAR(120) NOT NULL,
        TRIGGER_NAME VARCHAR(200) NOT NULL,
        TRIGGER_GROUP VARCHAR(200) NOT NULL,
        REPEAT_COUNT BIGINT NOT NULL,
        REPEAT_INTERVAL BIGINT NOT NULL,
        TIMES_TRIGGERED BIGINT NOT NULL,
        PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP),
        FOREIGN KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP) REFERENCES QRTZ_TRIGGERS (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
    );

CREATE TABLE
    public.QRTZ_CRON_TRIGGERS (
        SCHED_NAME VARCHAR(120) NOT NULL,
        TRIGGER_NAME VARCHAR(200) NOT NULL,
        TRIGGER_GROUP VARCHAR(200) NOT NULL,
        CRON_EXPRESSION VARCHAR(120) NOT NULL,
        TIME_ZONE_ID VARCHAR(80),
        PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP),
        FOREIGN KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP) REFERENCES QRTZ_TRIGGERS (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
    );

CREATE TABLE
    public.QRTZ_SIMPROP_TRIGGERS (
        SCHED_NAME VARCHAR(120) NOT NULL,
        TRIGGER_NAME VARCHAR(200) NOT NULL,
        TRIGGER_GROUP VARCHAR(200) NOT NULL,
        STR_PROP_1 VARCHAR(512) NULL,
        STR_PROP_2 VARCHAR(512) NULL,
        STR_PROP_3 VARCHAR(512) NULL,
        INT_PROP_1 INT NULL,
        INT_PROP_2 INT NULL,
        LONG_PROP_1 BIGINT NULL,
        LONG_PROP_2 BIGINT NULL,
        DEC_PROP_1 NUMERIC(13, 4) NULL,
        DEC_PROP_2 NUMERIC(13, 4) NULL,
        BOOL_PROP_1 BOOL NULL,
        BOOL_PROP_2 BOOL NULL,
        PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP),
        FOREIGN KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP) REFERENCES QRTZ_TRIGGERS (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
    );

CREATE TABLE
    public.QRTZ_BLOB_TRIGGERS (
        SCHED_NAME VARCHAR(120) NOT NULL,
        TRIGGER_NAME VARCHAR(200) NOT NULL,
        TRIGGER_GROUP VARCHAR(200) NOT NULL,
        BLOB_DATA BYTEA NULL,
        PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP),
        FOREIGN KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP) REFERENCES QRTZ_TRIGGERS (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
    );

CREATE TABLE
    public.QRTZ_CALENDARS (
        SCHED_NAME VARCHAR(120) NOT NULL,
        CALENDAR_NAME VARCHAR(200) NOT NULL,
        CALENDAR BYTEA NOT NULL,
        PRIMARY KEY (SCHED_NAME, CALENDAR_NAME)
    );

CREATE TABLE
    public.QRTZ_PAUSED_TRIGGER_GRPS (SCHED_NAME VARCHAR(120) NOT NULL, TRIGGER_GROUP VARCHAR(200) NOT NULL, PRIMARY KEY (SCHED_NAME, TRIGGER_GROUP));

CREATE TABLE
    public.QRTZ_FIRED_TRIGGERS (
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
        IS_NONCONCURRENT BOOL NULL,
        REQUESTS_RECOVERY BOOL NULL,
        PRIMARY KEY (SCHED_NAME, ENTRY_ID)
    );

CREATE TABLE
    public.QRTZ_SCHEDULER_STATE (
        SCHED_NAME VARCHAR(120) NOT NULL,
        INSTANCE_NAME VARCHAR(200) NOT NULL,
        LAST_CHECKIN_TIME BIGINT NOT NULL,
        CHECKIN_INTERVAL BIGINT NOT NULL,
        PRIMARY KEY (SCHED_NAME, INSTANCE_NAME)
    );

CREATE TABLE
    public.QRTZ_LOCKS (SCHED_NAME VARCHAR(120) NOT NULL, LOCK_NAME VARCHAR(40) NOT NULL, PRIMARY KEY (SCHED_NAME, LOCK_NAME));

CREATE INDEX IDX_QRTZ_J_REQ_RECOVERY ON public.QRTZ_JOB_DETAILS (SCHED_NAME, REQUESTS_RECOVERY);

CREATE INDEX IDX_QRTZ_J_GRP ON public.QRTZ_JOB_DETAILS (SCHED_NAME, JOB_GROUP);

CREATE INDEX IDX_QRTZ_T_J ON public.QRTZ_TRIGGERS (SCHED_NAME, JOB_NAME, JOB_GROUP);

CREATE INDEX IDX_QRTZ_T_JG ON public.QRTZ_TRIGGERS (SCHED_NAME, JOB_GROUP);

CREATE INDEX IDX_QRTZ_T_C ON public.QRTZ_TRIGGERS (SCHED_NAME, CALENDAR_NAME);

CREATE INDEX IDX_QRTZ_T_G ON public.QRTZ_TRIGGERS (SCHED_NAME, TRIGGER_GROUP);

CREATE INDEX IDX_QRTZ_T_STATE ON public.QRTZ_TRIGGERS (SCHED_NAME, TRIGGER_STATE);

CREATE INDEX IDX_QRTZ_T_N_STATE ON public.QRTZ_TRIGGERS (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP, TRIGGER_STATE);

CREATE INDEX IDX_QRTZ_T_N_G_STATE ON public.QRTZ_TRIGGERS (SCHED_NAME, TRIGGER_GROUP, TRIGGER_STATE);

CREATE INDEX IDX_QRTZ_T_NEXT_FIRE_TIME ON public.QRTZ_TRIGGERS (SCHED_NAME, NEXT_FIRE_TIME);

CREATE INDEX IDX_QRTZ_T_NFT_ST ON public.QRTZ_TRIGGERS (SCHED_NAME, TRIGGER_STATE, NEXT_FIRE_TIME);

CREATE INDEX IDX_QRTZ_T_NFT_MISFIRE ON public.QRTZ_TRIGGERS (SCHED_NAME, MISFIRE_INSTR, NEXT_FIRE_TIME);

CREATE INDEX IDX_QRTZ_T_NFT_ST_MISFIRE ON public.QRTZ_TRIGGERS (SCHED_NAME, MISFIRE_INSTR, NEXT_FIRE_TIME, TRIGGER_STATE);

CREATE INDEX IDX_QRTZ_T_NFT_ST_MISFIRE_GRP ON public.QRTZ_TRIGGERS (SCHED_NAME, MISFIRE_INSTR, NEXT_FIRE_TIME, TRIGGER_GROUP, TRIGGER_STATE);

CREATE INDEX IDX_QRTZ_FT_TRIG_INST_NAME ON public.QRTZ_FIRED_TRIGGERS (SCHED_NAME, INSTANCE_NAME);

CREATE INDEX IDX_QRTZ_FT_INST_JOB_REQ_RCVRY ON public.QRTZ_FIRED_TRIGGERS (SCHED_NAME, INSTANCE_NAME, REQUESTS_RECOVERY);

CREATE INDEX IDX_QRTZ_FT_J_G ON public.QRTZ_FIRED_TRIGGERS (SCHED_NAME, JOB_NAME, JOB_GROUP);

CREATE INDEX IDX_QRTZ_FT_JG ON public.QRTZ_FIRED_TRIGGERS (SCHED_NAME, JOB_GROUP);

CREATE INDEX IDX_QRTZ_FT_T_G ON public.QRTZ_FIRED_TRIGGERS (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP);

CREATE INDEX IDX_QRTZ_FT_TG ON public.QRTZ_FIRED_TRIGGERS (SCHED_NAME, TRIGGER_GROUP);