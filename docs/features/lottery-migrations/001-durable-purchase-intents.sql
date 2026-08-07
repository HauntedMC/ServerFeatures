-- Run once during a Lottery maintenance window before enabling native Economy ticket purchases.
-- Execute with the deployment/migration account; the runtime account must not have DDL privileges.

CREATE TABLE system_lottery_purchase_intents (
    id VARCHAR(36) NOT NULL,
    lottery_key VARCHAR(64) NOT NULL,
    round_id VARCHAR(36) NOT NULL,
    player_id BIGINT NOT NULL,
    player_uuid VARCHAR(36) NOT NULL,
    player_name VARCHAR(32) NOT NULL,
    ticket_count INT NOT NULL,
    charged_amount DECIMAL(19,2) NOT NULL,
    state VARCHAR(24) NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_lottery_purchase_intent_round (lottery_key, round_id, state),
    INDEX idx_lottery_purchase_intent_player (lottery_key, player_uuid, state),
    CONSTRAINT chk_lottery_purchase_intent_tickets CHECK (ticket_count > 0),
    CONSTRAINT chk_lottery_purchase_intent_amount CHECK (charged_amount > 0)
) ENGINE=InnoDB;
