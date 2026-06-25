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
