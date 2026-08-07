-- Run once, during a full Economy maintenance window, before enabling chargeAndDispatch.
-- Execute using the deployment/migration account. The runtime account must not receive DDL rights.

CREATE TABLE system_economy_workflow (
    event_id VARCHAR(36) NOT NULL,
    source VARCHAR(64) NOT NULL,
    workflow_key VARCHAR(160) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    operation_id VARCHAR(36) NOT NULL,
    player_id BIGINT NOT NULL,
    player_uuid VARCHAR(36) NOT NULL,
    player_name VARCHAR(32) NOT NULL,
    currency_id VARCHAR(64) NOT NULL,
    scope_key VARCHAR(128) NOT NULL,
    amount DECIMAL(38,8) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    metadata_json VARCHAR(4096) NOT NULL,
    state VARCHAR(24) NOT NULL,
    attempts INT NOT NULL,
    available_at BIGINT NOT NULL,
    lease_owner VARCHAR(64) NULL,
    lease_expires_at BIGINT NULL,
    last_error VARCHAR(512) NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    delivered_at BIGINT NULL,
    PRIMARY KEY (event_id),
    CONSTRAINT uq_economy_workflow_source_key UNIQUE (source, workflow_key),
    CONSTRAINT uq_economy_workflow_operation UNIQUE (operation_id),
    INDEX idx_economy_workflow_ready (state, available_at, created_at),
    INDEX idx_economy_workflow_event_type (event_type, state, available_at)
) ENGINE=InnoDB;
