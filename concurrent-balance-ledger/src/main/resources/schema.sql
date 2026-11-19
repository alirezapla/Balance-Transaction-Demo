CREATE TABLE IF NOT EXISTS account (id BIGSERIAL PRIMARY KEY, account_id VARCHAR(100) NOT NULL UNIQUE, account_type VARCHAR(20) NOT NULL CHECK(account_type IN ('WALLET','SYSTEM')), normal_balance_side VARCHAR(10) NOT NULL CHECK(normal_balance_side IN ('DEBIT','CREDIT')), balance BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), CHECK(account_type <> 'WALLET' OR balance >= 0));
CREATE TABLE IF NOT EXISTS financial_transaction (id BIGSERIAL PRIMARY KEY, transaction_id VARCHAR(150) NOT NULL UNIQUE, transaction_type VARCHAR(30) NOT NULL, request_fingerprint VARCHAR(512) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW());
CREATE TABLE IF NOT EXISTS ledger_entry (id BIGSERIAL PRIMARY KEY, financial_transaction_id BIGINT NOT NULL REFERENCES financial_transaction(id), account_id BIGINT NOT NULL REFERENCES account(id), entry_side VARCHAR(10) NOT NULL CHECK(entry_side IN ('DEBIT','CREDIT')), amount BIGINT NOT NULL CHECK(amount > 0), created_at TIMESTAMPTZ NOT NULL DEFAULT NOW());
CREATE INDEX IF NOT EXISTS idx_ledger_account ON ledger_entry(account_id,id);
CREATE INDEX IF NOT EXISTS idx_ledger_tx ON ledger_entry(financial_transaction_id);
INSERT INTO account(account_id,account_type,normal_balance_side,balance) VALUES('SYSTEM_CLEARING','SYSTEM','DEBIT',0) ON CONFLICT(account_id) DO NOTHING;

CREATE TABLE IF NOT EXISTS reconciliation_run (
    id BIGSERIAL PRIMARY KEY,
    status VARCHAR(20) NOT NULL CHECK (status IN ('RUNNING','SUCCESS','INCONSISTENT','FAILED')),
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    account_count INTEGER NOT NULL DEFAULT 0,
    inconsistent_account_count INTEGER NOT NULL DEFAULT 0,
    error_message VARCHAR(2000)
);
CREATE INDEX IF NOT EXISTS idx_reconciliation_run_started_at ON reconciliation_run(started_at DESC);
CREATE INDEX IF NOT EXISTS idx_reconciliation_run_status ON reconciliation_run(status);

CREATE TABLE IF NOT EXISTS reconciliation_difference (
    id BIGSERIAL PRIMARY KEY,
    reconciliation_run_id BIGINT NOT NULL REFERENCES reconciliation_run(id) ON DELETE CASCADE,
    account_id VARCHAR(100) NOT NULL,
    stored_balance BIGINT NOT NULL,
    ledger_balance BIGINT NOT NULL,
    difference BIGINT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_reconciliation_difference_run ON reconciliation_difference(reconciliation_run_id);

CREATE TABLE IF NOT EXISTS shedlock (
    name VARCHAR(64) NOT NULL PRIMARY KEY,
    lock_until TIMESTAMPTZ NOT NULL,
    locked_at TIMESTAMPTZ NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);
