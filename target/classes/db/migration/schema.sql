CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    phone VARCHAR(20) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    bvn VARCHAR(11) UNIQUE,
    nin VARCHAR(11) UNIQUE,
    kyc_tier VARCHAR(10) NOT NULL DEFAULT 'TIER_1',
    kyc_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE wallets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID UNIQUE NOT NULL REFERENCES users(id),
    account_number VARCHAR(10) UNIQUE NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'NGN',
    balance DECIMAL(19,4) NOT NULL DEFAULT 0.00,
    ledger_balance DECIMAL(19,4) NOT NULL DEFAULT 0.00,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE wallet_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id UUID NOT NULL REFERENCES wallets(id),
    type VARCHAR(10) NOT NULL,
    amount DECIMAL(19,4) NOT NULL,
    fee DECIMAL(19,4) NOT NULL DEFAULT 0.00,
    balance_before DECIMAL(19,4) NOT NULL,
    balance_after DECIMAL(19,4) NOT NULL,
    reference VARCHAR(100) UNIQUE NOT NULL,
    description VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    transaction_type VARCHAR(50),
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE transfers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sender_wallet_id UUID NOT NULL REFERENCES wallets(id),
    receiver_wallet_id UUID NOT NULL REFERENCES wallets(id),
    amount DECIMAL(19,4) NOT NULL,
    fee DECIMAL(19,4) NOT NULL DEFAULT 0.00,
    reference VARCHAR(100) UNIQUE NOT NULL,
    narration VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    sender_transaction_id UUID REFERENCES wallet_transactions(id),
    receiver_transaction_id UUID REFERENCES wallet_transactions(id),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE kyc_tier_limits (
    tier VARCHAR(10) PRIMARY KEY,
    single_transfer_limit DECIMAL(19,4) NOT NULL,
    daily_debit_limit DECIMAL(19,4) NOT NULL,
    max_wallet_balance DECIMAL(19,4) NOT NULL
);

INSERT INTO kyc_tier_limits (tier, single_transfer_limit, daily_debit_limit, max_wallet_balance) VALUES
    ('TIER_1', 50000.00, 300000.00, 300000.00),
    ('TIER_2', 200000.00, 500000.00, 500000.00),
    ('TIER_3', 1000000.00, 5000000.00, 99999999.00);



    CREATE TABLE nip_transactions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sender_wallet_id    UUID NOT NULL REFERENCES wallets(id),
    sender_account      VARCHAR(10) NOT NULL,
    receiver_account    VARCHAR(10) NOT NULL,
    receiver_bank_code  VARCHAR(6) NOT NULL,
    receiver_name       VARCHAR(255),
    amount              DECIMAL(19,4) NOT NULL,
    fee                 DECIMAL(19,4) DEFAULT 50.00,
    reference           VARCHAR(100) UNIQUE NOT NULL,
    session_id          VARCHAR(100) UNIQUE,  -- NIBSS session ID
    narration           VARCHAR(255),
    status              VARCHAR(30) DEFAULT 'PENDING',
    -- PENDING, PROCESSING, SUCCESS, FAILED, TIMEOUT, REVERSED
    response_code       VARCHAR(10),
    response_message    VARCHAR(255),
    retry_count         INT DEFAULT 0,
    nibss_sent_at       TIMESTAMP,
    nibss_responded_at  TIMESTAMP,
    created_at          TIMESTAMP DEFAULT now(),
    updated_at          TIMESTAMP DEFAULT now()
);



-- General Ledger accounts (Chart of Accounts)
CREATE TABLE gl_accounts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(20) UNIQUE NOT NULL,
    name            VARCHAR(255) NOT NULL,
    type            VARCHAR(20) NOT NULL,  -- ASSET, LIABILITY, INCOME, EXPENSE, EQUITY
    normal_balance  VARCHAR(10) NOT NULL,  -- DEBIT or CREDIT (natural side)
    description     VARCHAR(255),
    is_active       BOOLEAN DEFAULT true,
    created_at      TIMESTAMP DEFAULT now()
);

-- Every financial event posts TWO entries here (double-entry)
CREATE TABLE gl_entries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    gl_account_id   UUID NOT NULL REFERENCES gl_accounts(id),
    entry_type      VARCHAR(10) NOT NULL,   -- DEBIT or CREDIT
    amount          DECIMAL(19,4) NOT NULL,
    reference       VARCHAR(100) NOT NULL,  -- links to wallet_transaction reference
    narration       VARCHAR(255),
    transaction_date DATE NOT NULL,
    posted_at       TIMESTAMP DEFAULT now()
);

-- AML flags
CREATE TABLE aml_flags (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id),
    wallet_id           UUID NOT NULL REFERENCES wallets(id),
    transaction_ref     VARCHAR(100),
    flag_type           VARCHAR(50) NOT NULL,
    -- LARGE_TRANSACTION, HIGH_FREQUENCY, STRUCTURING,
    -- UNUSUAL_PATTERN, DAILY_LIMIT_BREACH
    amount              DECIMAL(19,4),
    description         VARCHAR(500),
    status              VARCHAR(20) DEFAULT 'OPEN',  -- OPEN, REVIEWED, CLEARED, ESCALATED
    reviewed_by         VARCHAR(255),
    reviewed_at         TIMESTAMP,
    created_at          TIMESTAMP DEFAULT now()
);

-- KYC documents and upgrade requests
CREATE TABLE kyc_upgrades (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id),
    requested_tier      VARCHAR(10) NOT NULL,
    bvn                 VARCHAR(11),
    nin                 VARCHAR(11),
    address             VARCHAR(500),
    document_type       VARCHAR(50),   -- NIN_SLIP, PASSPORT, DRIVERS_LICENSE
    document_number     VARCHAR(100),
    status              VARCHAR(20) DEFAULT 'PENDING',
    -- PENDING, APPROVED, REJECTED
    rejection_reason    VARCHAR(500),
    reviewed_at         TIMESTAMP,
    created_at          TIMESTAMP DEFAULT now()
);

-- Daily settlement report
CREATE TABLE settlement_reports (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_date         DATE UNIQUE NOT NULL,
    total_credits       DECIMAL(19,4) DEFAULT 0,
    total_debits        DECIMAL(19,4) DEFAULT 0,
    total_nip_outward   DECIMAL(19,4) DEFAULT 0,
    total_nip_inward    DECIMAL(19,4) DEFAULT 0,
    total_fees          DECIMAL(19,4) DEFAULT 0,
    total_reversals     DECIMAL(19,4) DEFAULT 0,
    transaction_count   INT DEFAULT 0,
    nip_count           INT DEFAULT 0,
    aml_flags_raised    INT DEFAULT 0,
    net_position        DECIMAL(19,4) DEFAULT 0,
    status              VARCHAR(20) DEFAULT 'DRAFT', -- DRAFT, FINAL
    generated_at        TIMESTAMP DEFAULT now()
);

-- Seed GL accounts (Chart of Accounts)
INSERT INTO gl_accounts (code, name, type, normal_balance, description) VALUES
('1001', 'Customer Wallet Funds',     'ASSET',     'DEBIT',  'Total funds held in customer wallets'),
('1002', 'NIBSS Settlement Account',  'ASSET',     'DEBIT',  'Funds at NIBSS for interbank settlement'),
('1003', 'Cash and Bank',             'ASSET',     'DEBIT',  'Physical cash and bank balances'),
('2001', 'Customer Wallet Liability', 'LIABILITY', 'CREDIT', 'What we owe customers in wallets'),
('2002', 'Payable to NIBSS',          'LIABILITY', 'CREDIT', 'Outward NIP transfers pending settlement'),
('4001', 'Transfer Fee Income',       'INCOME',    'CREDIT', 'Revenue from transfer fees'),
('4002', 'Other Fee Income',          'INCOME',    'CREDIT', 'Miscellaneous fee revenue'),
('5001', 'NIBSS Settlement Expense',  'EXPENSE',   'DEBIT',  'Cost of interbank settlement');