# MFB Wallet Service

A production-grade digital banking and wallet backend built with **Java Spring Boot**, simulating the core infrastructure of a Microfinance Bank (MFB) — wallet management, NIBSS Instant Payment (NIP) interbank transfers, CBN-compliant KYC tiering, AML transaction monitoring, and double-entry General Ledger accounting.

This project was built to demonstrate real fintech engineering: not CRUD endpoints, but the actual hard problems backend engineers solve at companies like Moniepoint, Kuda, OPay, and PalmPay — idempotency, concurrency-safe money movement, interbank timeout handling, and regulatory compliance enforced in code.

---



## Why This Project Exists

Most backend portfolio projects are CRUD apps with auth bolted on. This project instead answers a specific question: **what does it actually take to move money correctly?**

Every design decision here mirrors how real MFBs and fintechs in Nigeria operate:

- Money is never just "updated" — every movement is a recorded, auditable transaction
- Every transfer is idempotent — network retries can never double-charge a customer
- Every transaction posts to a double-entry General Ledger that must always balance
- CBN KYC tier limits are enforced at the code level, not just documented
- Interbank transfers (NIP) handle the hardest real-world case: the timeout, where you genuinely don't know if money moved
- Large or suspicious transactions are automatically flagged for AML review

---

## Architecture Overview

```
┌─────────────┐      ┌──────────────────┐      ┌─────────────┐
│   Client     │─────▶│  Spring Boot API │─────▶│ PostgreSQL  │
│ (Postman/App)│      │   (REST + JWT)   │      │  (Source of │
└─────────────┘      └────────┬─────────┘      │    Truth)   │
                               │                 └─────────────┘
                      ┌────────┴─────────┐
                      │      Redis        │
                      │ (Cache + Rate     │
                      │   Limiting)       │
                      └────────┬─────────┘
                               │
                      ┌────────┴─────────┐
                      │  NIBSS Gateway    │
                      │   (Simulated)     │
                      │  Name Enquiry,    │
                      │  Transfer, Status │
                      └──────────────────┘
```

**Request flow for a typical transfer:**

```
1. Client authenticates → receives JWT
2. Client calls /wallet/transfer with an idempotency reference
3. Service checks rate limit (Redis)
4. Service checks idempotency (has this reference been seen before?)
5. Service validates KYC tier limits
6. Service locks both wallets (pessimistic lock, prevents race conditions)
7. Debit sender, credit receiver — atomic, both succeed or both roll back
8. Transaction posted to General Ledger (double-entry)
9. Transaction screened by AML rules engine (async)
10. Redis cache invalidated for both wallets
11. Response returned to client
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.x |
| Database | PostgreSQL |
| Cache / Rate Limiting | Redis |
| Auth | Spring Security + JWT (jjwt) |
| ORM | Spring Data JPA / Hibernate |
| API Docs | SpringDoc OpenAPI (Swagger UI) |
| Build Tool | Maven |
| Interbank Payments | Simulated NIBSS NIP Gateway |

---

## Core Concepts

### 1. Wallets are ledgers, not balance fields

A wallet's balance is never blindly overwritten. Every credit or debit is recorded as a `WalletTransaction` with a `balanceBefore` and `balanceAfter` snapshot. This gives a full, tamper-evident audit trail for every kobo that moves.

### 2. Idempotency is enforced, not assumed

Every transfer request requires a client-generated `reference`. If the same reference is submitted twice — due to a network retry, a double-tap, or a client bug — the system detects the duplicate and returns the original result instead of processing the transfer again.

```java
Optional<Transfer> existing = transferRepository.findByReference(request.getReference());
if (existing.isPresent()) {
    return mapToResponse(existing.get()); // no double-spend
}
```

### 3. Concurrency safety via pessimistic locking

Two simultaneous transfer requests against the same wallet cannot both read a stale balance and both succeed. Wallet rows are locked (`PESSIMISTIC_WRITE`) during the debit/credit operation, serializing access and preventing race conditions.

### 4. KYC tiers enforce CBN-style limits

Every transfer is checked against the sender's KYC tier before it's allowed to proceed:

| Tier | Requirement | Single Transfer Limit | Daily Debit Limit | Max Wallet Balance |
|---|---|---|---|---|
| Tier 1 | Phone number only | ₦50,000 | ₦300,000 | ₦300,000 |
| Tier 2 | + BVN | ₦200,000 | ₦500,000 | ₦500,000 |
| Tier 3 | + NIN + Address | ₦1,000,000 | ₦5,000,000 | Unlimited |

### 5. NIP transfers handle the timeout problem correctly

The hardest part of any interbank integration: NIBSS doesn't respond in time. Did the money move or not? This system handles it the way real payment systems do:

```
Transfer sent → NIBSS times out
   → Mark transaction as TIMEOUT (not failed, not success)
   → Trigger status enquiry with exponential backoff (3 retries)
       → If confirmed successful → mark SUCCESS, keep the debit
       → If confirmed failed after all retries → reverse the debit, mark REVERSED
```

The customer's money is never left in limbo, and it's never silently lost or duplicated.

### 6. Every transaction posts to a double-entry General Ledger

This isn't just a wallet balance tracker — it's accounting infrastructure. Every financial event posts two GL entries (a debit and a credit) to maintain the fundamental rule of accounting:

```
Total Debits === Total Credits, always.
```

A `/admin/gl/trial-balance` endpoint exists specifically to verify this invariant holds at any point in time.

### 7. AML screening runs on every transaction

Transactions are automatically screened against CBN-aligned rules:

- **Large transaction** — above ₦5,000,000 (CBN Cash Transaction Report threshold)
- **Structuring** — amounts suspiciously just under the ₦5m threshold
- **High frequency** — more than 20 transactions in an hour
- **Unusual velocity** — more than ₦1,000,000 moved in an hour

Flagged transactions go into a compliance queue for manual review via the admin API.

---

## Database Schema

```
users ──┬──< addresses
        ├──1:1── wallets ──┬──< wallet_transactions
        │                   ├──< transfers (sender)
        │                   └──< nip_transactions
        ├──< kyc_upgrades
        └──< aml_flags

gl_accounts ──< gl_entries

settlement_reports (daily aggregate snapshot)
```

**Key relationship decisions:**

- `User` → `Wallet` is one-to-one. One wallet per user in this version; multi-currency wallets are a planned extension.
- `WalletTransaction.unit_price`-style snapshotting is used throughout — historical records never depend on current state of another table. A transaction shows exactly what the balance was at that moment, permanently.
- `Transfer` links two `WalletTransaction` records (sender's debit, receiver's credit) so a single transfer can be traced from either side.
- `GlEntry` always comes in pairs per transaction reference, enforced at the service layer (`postDoubleEntry`).

Full SQL schema is in [`/schema.sql`](./schema.sql).

---

## API Documentation

Interactive API docs are available via Swagger UI once the app is running:

```
http://localhost:8080/swagger-ui/index.html
```

### Endpoint Summary

**Auth**
```
POST   /api/v1/auth/register      Register user, auto-creates wallet
POST   /api/v1/auth/login         Returns JWT (24h expiry)
```

**Wallet**
```
GET    /api/v1/wallet/balance         Get balance (Redis-cached, 30s TTL)
POST   /api/v1/wallet/transfer        Intra-platform wallet transfer
GET    /api/v1/wallet/transactions    Paginated transaction history
```

**NIP (Interbank Transfers)**
```
GET    /api/v1/transfer/name-enquiry  Verify recipient account name
POST   /api/v1/transfer/interbank     Send money to another bank
```

**KYC**
```
GET    /api/v1/kyc/status     Current tier, limits, upgrade eligibility
POST   /api/v1/kyc/upgrade    Submit documents for tier upgrade
```

**Admin / Compliance**
```
GET    /api/v1/admin/aml/flags                    List open AML flags
PATCH  /api/v1/admin/aml/flags/{id}/review         Review/clear a flag
GET    /api/v1/admin/settlement/{date}             Daily settlement report
GET    /api/v1/admin/gl/trial-balance              Verify GL is balanced
GET    /api/v1/admin/gl/account/{code}/balance     Single GL account balance
```

A full Postman collection with pre-scripted auth, idempotency tests, and edge case coverage is included: [`/postman/MFB-Wallet-API.postman_collection.json`](./postman/MFB-Wallet-API.postman_collection.json)

---

## Getting Started

### Prerequisites

```
Java 17+
Maven 3.8+
PostgreSQL 14+
Redis 6+
```

### 1. Clone and configure

```bash
git clone https://github.com/<your-username>/mfb-wallet-service.git
cd mfb-wallet-service
```

### 2. Create the database

```bash
createdb wallet_db
psql wallet_db < schema.sql
```

### 3. Configure `application.yml`

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/wallet_db
    username: postgres
    password: <your-password>
  redis:
    host: localhost
    port: 6379

app:
  jwt:
    secret: <generate-a-long-random-base64-secret>
```

### 4. Run

```bash
mvn spring-boot:run
```

The API is now live at `http://localhost:8080` and Swagger UI at `http://localhost:8080/swagger-ui/index.html`.

### 5. Try the full flow

```bash
# Register
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"fullName":"Test User","email":"test@example.com","phone":"08012345678","password":"SecurePass123!"}'

# Login
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"SecurePass123!"}'

# Use the returned token for subsequent requests
curl http://localhost:8080/api/v1/wallet/balance \
  -H "Authorization: Bearer <token>"
```

Or simply import the Postman collection and run it end to end.

---

## Testing

```bash
mvn test
```

Key scenarios covered:

- Idempotent transfer requests do not double-process
- Concurrent transfers against the same wallet do not cause balance corruption
- KYC tier limits are correctly enforced per tier
- NIP timeout → status enquiry → reversal flow resolves correctly
- General Ledger remains balanced after every transaction type (funding, transfer, NIP, reversal)

---

## Design Decisions

**Why BigDecimal, not double or float, for money?**
Floating point arithmetic introduces rounding errors that are unacceptable in financial systems. `BigDecimal` with explicit scale (`precision = 19, scale = 4`) guarantees exact decimal representation.

**Why snapshot the price/balance at transaction time instead of computing it live?**
Source data changes — product prices update, balances move. A historical transaction record must show what was true *at that moment*, not what's true now. This is standard practice in any system handling financial records.

**Why pessimistic locking instead of optimistic locking for wallets?**
Wallet transfers are high-contention, low-latency operations where a failed optimistic lock (requiring a retry) is worse UX than a brief wait for a pessimistic lock. For lower-contention entities (like updating a user's profile), optimistic locking would be preferred instead.

**Why is the debit posted before calling NIBSS, with reversal on failure, rather than after confirmation?**
This mirrors how real NIP integrations work — funds are held the moment a transfer is initiated to prevent the same money being spent twice while a transfer is in flight. The reversal path exists specifically to handle the case where NIBSS ultimately fails or times out.

**Why a separate `gl_entries` table instead of just trusting `wallet_transactions`?**
The wallet ledger answers "what is this customer's balance." The General Ledger answers "is the bank's entire balance sheet correct." These are different questions serving different audiences (customer-facing vs. regulatory/accounting), and conflating them would make both harder to reason about.

---

## Project Structure

```
src/main/java/com/wallet/
├── config/              JWT, Redis, OpenAPI, Security configuration
├── controller/          REST endpoints
├── service/             Business logic (wallet, transfer, NIP, GL, AML, KYC)
├── repository/          Spring Data JPA repositories
├── entity/              JPA entities
├── dto/
│   ├── request/         Inbound request bodies
│   └── response/        Outbound response bodies
├── enums/                TransactionType, KycTier, NipStatus, AmlFlagType
├── gateway/              Simulated NIBSS gateway client
└── exception/            Global exception handling
```

---

## Roadmap

```
[ ] Async AML screening via message queue (Kafka) instead of @Async
[ ] Multi-currency wallet support
[ ] Real NIBSS sandbox integration (replacing the simulator)
[ ] Webhook notifications for transaction events
[ ] Scheduled job infrastructure for settlement reports (currently inline)
[ ] Admin dashboard (separate frontend) for AML review and GL inspection
[ ] Card issuance simulation layer
```

---

## License

This is a portfolio/learning project and is not licensed for production financial use without independent security review, regulatory compliance verification, and a real (not simulated) NIBSS integration.