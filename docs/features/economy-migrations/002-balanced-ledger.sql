-- Run once, during a full Economy maintenance window, before deploying the balanced-ledger build.
-- Back up the database first. The runtime account must only have DML privileges; execute this
-- migration using the deployment/migration role and return the runtime ORM setting to `validate`.

ALTER TABLE system_economy_transaction_entry
    ADD COLUMN account_kind VARCHAR(16) NOT NULL DEFAULT 'PLAYER' AFTER player_id;

ALTER TABLE system_economy_transaction_entry
    MODIFY COLUMN balance_before DECIMAL(38,8) NULL,
    MODIFY COLUMN balance_after DECIMAL(38,8) NULL;

-- Historical issuing, burning and account-provisioning transactions had one player entry. Add
-- their logical issuance counter-entry so every monetary transaction sums to zero. The migration
-- is idempotent and intentionally does not create a mutable global treasury balance row.
INSERT INTO system_economy_transaction_entry
    (transaction_id, account_id, player_id, account_kind, entry_role, delta, balance_before, balance_after)
SELECT t.id,
       CONCAT('system:issuance:', t.currency_id, ':', SHA2(t.scope_key, 256)),
       0,
       'SYSTEM',
       'SYSTEM',
       -target.delta,
       NULL,
       NULL
FROM system_economy_transaction t
JOIN system_economy_transaction_entry target
    ON target.transaction_id = t.id
   AND target.entry_role = 'TARGET'
WHERE t.transaction_type IN ('ACCOUNT_CREATED', 'DEPOSIT', 'WITHDRAW', 'SET')
  AND NOT EXISTS (
      SELECT 1
      FROM system_economy_transaction_entry existing
      WHERE existing.transaction_id = t.id AND existing.entry_role = 'SYSTEM'
  );

-- Verify before allowing traffic:
SELECT t.id, t.operation_id
FROM system_economy_transaction t
LEFT JOIN system_economy_transaction_entry e ON e.transaction_id = t.id
GROUP BY t.id, t.operation_id
HAVING SUM(e.delta) <> 0;
