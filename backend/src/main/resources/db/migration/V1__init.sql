CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE orders (
    order_id   VARCHAR(16) PRIMARY KEY,
    email      VARCHAR(255)   NOT NULL,
    status     VARCHAR(20)    NOT NULL,
    items      JSONB          NOT NULL,
    amount     NUMERIC(12, 2) NOT NULL,
    created_at TIMESTAMPTZ    NOT NULL
);

CREATE TABLE kb_article (
    id      VARCHAR(64) PRIMARY KEY,
    title   VARCHAR(255) NOT NULL,
    body_md TEXT         NOT NULL
);

CREATE TABLE ticket (
    id             BIGSERIAL PRIMARY KEY,
    text           TEXT        NOT NULL,
    customer_email VARCHAR(255),
    source         VARCHAR(16) NOT NULL,
    status         VARCHAR(16) NOT NULL,
    triage_error   TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ticket_status_idx ON ticket (status);
CREATE INDEX ticket_created_idx ON ticket (created_at);

CREATE TABLE triage_result (
    id          BIGSERIAL PRIMARY KEY,
    ticket_id   BIGINT      NOT NULL REFERENCES ticket (id) ON DELETE CASCADE,
    category    VARCHAR(16),
    priority    VARCHAR(4),
    queue       VARCHAR(32),
    needs_human BOOLEAN     NOT NULL,
    model       VARCHAR(64) NOT NULL,
    result      JSONB       NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX triage_result_ticket_idx ON triage_result (ticket_id, id DESC);
CREATE INDEX triage_result_filter_idx ON triage_result (category, priority, needs_human);

-- Latest triage per ticket; retriage adds a row, history is kept.
ALTER TABLE ticket ADD COLUMN current_result_id BIGINT REFERENCES triage_result (id) ON DELETE SET NULL;

CREATE TABLE reply_decision (
    id         BIGSERIAL PRIMARY KEY,
    ticket_id  BIGINT      NOT NULL REFERENCES ticket (id) ON DELETE CASCADE,
    result_id  BIGINT REFERENCES triage_result (id) ON DELETE SET NULL,
    action     VARCHAR(16) NOT NULL,
    final_text TEXT,
    note       TEXT,
    decided_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX reply_decision_ticket_idx ON reply_decision (ticket_id);

CREATE TABLE eval_run (
    id          BIGSERIAL PRIMARY KEY,
    label       VARCHAR(64) NOT NULL,
    config      JSONB       NOT NULL,
    status      VARCHAR(16) NOT NULL,
    metrics     JSONB,
    total_cases INT         NOT NULL,
    done_cases  INT         NOT NULL DEFAULT 0,
    error       TEXT,
    started_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at TIMESTAMPTZ
);

CREATE TABLE eval_case_result (
    id         BIGSERIAL PRIMARY KEY,
    run_id     BIGINT      NOT NULL REFERENCES eval_run (id) ON DELETE CASCADE,
    case_id    VARCHAR(16) NOT NULL,
    slice      VARCHAR(16) NOT NULL,
    correct    BOOLEAN     NOT NULL,
    latency_ms BIGINT      NOT NULL,
    detail     JSONB       NOT NULL
);
CREATE INDEX eval_case_result_run_idx ON eval_case_result (run_id);
