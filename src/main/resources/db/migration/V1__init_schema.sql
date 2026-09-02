CREATE TABLE release_notes
(
    id            BIGSERIAL PRIMARY KEY,
    repo_owner    VARCHAR(255)             NOT NULL,
    repo_name     VARCHAR(255)             NOT NULL,
    from_tag      VARCHAR(255)             NOT NULL,
    to_tag        VARCHAR(255)             NOT NULL,
    status        VARCHAR(20)              NOT NULL DEFAULT 'PROCESSING',
    content       TEXT,
    error_message TEXT,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT chk_release_notes_status
        CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_release_notes_repo
    ON release_notes (repo_owner, repo_name);

CREATE UNIQUE INDEX uq_release_notes_repo_tags
    ON release_notes (repo_owner, repo_name, from_tag, to_tag);

CREATE INDEX idx_release_notes_created_at
    ON release_notes (created_at DESC);

CREATE TABLE webhook_events
(
    id          BIGSERIAL PRIMARY KEY,
    delivery_id VARCHAR(255) UNIQUE,
    event_type  VARCHAR(100)             NOT NULL,
    repo_owner  VARCHAR(255),
    repo_name   VARCHAR(255),
    payload     JSONB,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    processed   BOOLEAN                  NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_webhook_events_processed
    ON webhook_events (processed);

CREATE
OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at
= now();
RETURN NEW;
END;
$$
LANGUAGE plpgsql;

CREATE TRIGGER trg_release_notes_updated_at
    BEFORE UPDATE
    ON release_notes
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();