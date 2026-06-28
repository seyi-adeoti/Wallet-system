package com.wallet.service;



@Service
@RequiredArgsConstructor
@Slf4j
public class GeneralLedgerService {

    private final GlAccountRepository glAccountRepository;
    private final GlEntryRepository glEntryRepository;

    // Every scenario posts specific GL accounts
    // Think of it like accounting journal entries

    // Scenario 1: Customer funds their wallet
    // DEBIT  1001 (Customer Wallet Funds) — asset increases
    // CREDIT 2001 (Customer Wallet Liability) — we owe customer more
    public void postWalletFunding(BigDecimal amount, String reference) {
        log.info("Posting wallet funding for amount={} and reference={}", amount, reference);
        postDoubleEntry(
            "1001", TransactionType.DEBIT,  amount, reference, "Wallet funding - asset in",
            "2001", TransactionType.CREDIT, amount, reference, "Wallet funding - liability to customer"
        );
    }

    // Scenario 2: Intra-wallet transfer (user A → user B)
    // No net change in total funds — just internal movement
    // DEBIT  2001 (Customer Wallet Liability) — we owe sender less
    // CREDIT 2001 (Customer Wallet Liability) — we owe receiver more
    // Net: zero. Which is correct — no money left the system
    public void postIntraWalletTransfer(BigDecimal amount, String reference,
                                         String senderAccount, String receiverAccount) {
        log.info("Posting intra-wallet transfer for amount={} and reference={} from={} to={}", amount, reference, senderAccount, receiverAccount);
        postDoubleEntry(
            "2001", TransactionType.DEBIT,  amount, reference,
                "Intra-transfer debit - " + senderAccount,
            "2001", TransactionType.CREDIT, amount, reference,
                "Intra-transfer credit - " + receiverAccount
        );
    }

    // Scenario 3: NIP Outward transfer
    // DEBIT  2001 (Customer Wallet Liability) — we owe sender less
    // CREDIT 2002 (Payable to NIBSS) — we now owe NIBSS this amount
    // Also post fee:
    // DEBIT  2001 (Customer Wallet Liability) — fee deducted from customer
    // CREDIT 4001 (Transfer Fee Income) — revenue recognized
    public void postNipOutwardTransfer(BigDecimal amount, BigDecimal fee,
                                        String reference, String senderAccount) {
        log.info("Posting NIP outward transfer for amount={} and reference={} from={}", amount, reference, senderAccount);
        // Main transfer
        postDoubleEntry(
            "2001", TransactionType.DEBIT,  amount, reference,
                "NIP outward - debit sender " + senderAccount,
            "2002", TransactionType.CREDIT, amount, reference,
                "NIP outward - payable to NIBSS"
        );

        // Fee entry
        if (fee.compareTo(BigDecimal.ZERO) > 0) {
            postDoubleEntry(
                "2001", TransactionType.DEBIT,  fee, reference + "-FEE",
                    "Transfer fee - " + senderAccount,
                "4001", TransactionType.CREDIT, fee, reference + "-FEE",
                    "Transfer fee income"
            );
        }
    }

    // Scenario 4: Reversal
    // Exact mirror of original entry
    public void postReversal(BigDecimal amount, BigDecimal fee,
                              String originalRef, String reversalRef) {
        log.info("Posting reversal for amount={} and reference={} originalRef={} reversalRef={}", amount, originalRef, reversalRef);
        postDoubleEntry(
            "2001", TransactionType.CREDIT, amount, reversalRef,
                "Reversal of " + originalRef,
            "2002", TransactionType.DEBIT,  amount, reversalRef,
                "Reversal - remove NIBSS payable"
        );

        if (fee.compareTo(BigDecimal.ZERO) > 0) {
            postDoubleEntry(
                "4001", TransactionType.DEBIT,  fee, reversalRef + "-FEE",
                    "Fee reversal",
                "2001", TransactionType.CREDIT, fee, reversalRef + "-FEE",
                    "Fee refund to customer"
            );
        }
    }

    // Core method — always posts two sides
    private void postDoubleEntry(
            String debitAccountCode,  TransactionType debitType,
            BigDecimal debitAmount,   String debitRef,   String debitNarration,
            String creditAccountCode, TransactionType creditType,
            BigDecimal creditAmount,  String creditRef,  String creditNarration) {

        log.info("Posting double entry for debitAccountCode={} debitType={} debitAmount={} debitRef={} debitNarration={} creditAccountCode={} creditType={} creditAmount={} creditRef={} creditNarration={}", debitAccountCode, debitType, debitAmount, debitRef, debitNarration, creditAccountCode, creditType, creditAmount, creditRef, creditNarration);
        // Sanity check — amounts must match
        if (debitAmount.compareTo(creditAmount) != 0) {
            throw new WalletException("GL posting error: debit and credit amounts must match");
        }

        GlAccount debitAccount = glAccountRepository.findByCode(debitAccountCode)
                .orElseThrow(() -> new WalletException("GL account not found: " + debitAccountCode));

        GlAccount creditAccount = glAccountRepository.findByCode(creditAccountCode)
                .orElseThrow(() -> new WalletException("GL account not found: " + creditAccountCode));

        GlEntry debitEntry = GlEntry.builder()
                .glAccount(debitAccount)
                .entryType(debitType)
                .amount(debitAmount)
                .reference(debitRef)
                .narration(debitNarration)
                .transactionDate(LocalDate.now())
                .build();

        GlEntry creditEntry = GlEntry.builder()
                .glAccount(creditAccount)
                .entryType(creditType)
                .amount(creditAmount)
                .reference(creditRef)
                .narration(creditNarration)
                .transactionDate(LocalDate.now())
                .build();

        glEntryRepository.saveAll(List.of(debitEntry, creditEntry));

        log.info("GL Posted → DR:{} CR:{} Amount:{} Ref:{}",
            debitAccountCode, creditAccountCode, debitAmount, debitRef);
    }

    // GL Balance for any account
    public BigDecimal getAccountBalance(String accountCode) {
        log.info("Getting account balance for accountCode={}", accountCode);
        GlAccount account = glAccountRepository.findByCode(accountCode).orElseThrow();
        log.info("Account found for accountCode={}", accountCode);
        BigDecimal totalDebits  = glEntryRepository.sumByAccountAndType(account.getId(), "DEBIT");
        BigDecimal totalCredits = glEntryRepository.sumByAccountAndType(account.getId(), "CREDIT");

        // For DEBIT-normal accounts (assets): balance = debits - credits
        // For CREDIT-normal accounts (liabilities, income): balance = credits - debits
        if ("DEBIT".equals(account.getNormalBalance())) {
            log.info("Account balance for accountCode={} is={}", accountCode, totalDebits.subtract(totalCredits));
            return totalDebits.subtract(totalCredits);
        } else {
            return totalCredits.subtract(totalDebits);
        }
    }

    // Trial balance — sum of all debits must equal sum of all credits
    public TrialBalanceResponse getTrialBalance(LocalDate date) {
        List<GlAccount> accounts = glAccountRepository.findAllActive();
        List<TrialBalanceLine> lines = new ArrayList<>();

        BigDecimal totalDebits  = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;

        for (GlAccount account : accounts) {
            BigDecimal debits  = glEntryRepository
                .sumByAccountAndTypeAndDate(account.getId(), "DEBIT", date);
            log.info("Debits for accountCode={} is={}", account.getCode(), debits);
            BigDecimal credits = glEntryRepository
                .sumByAccountAndTypeAndDate(account.getId(), "CREDIT", date);
            log.info("Credits for accountCode={} is={}", account.getCode(), credits);
            lines.add(TrialBalanceLine.builder()
                    .accountCode(account.getCode())
                    .accountName(account.getName())
                    .debits(debits)
                    .credits(credits)
                    .build());

            totalDebits  = totalDebits.add(debits);
            totalCredits = totalCredits.add(credits);
        }

        boolean balanced = totalDebits.compareTo(totalCredits) == 0;

        if (!balanced) {
            log.error("TRIAL BALANCE OUT OF BALANCE! Debits:{} Credits:{}",
                totalDebits, totalCredits);
        }

        return TrialBalanceResponse.builder()
                .date(date)
                .lines(lines)
                .totalDebits(totalDebits)
                .totalCredits(totalCredits)
                .balanced(balanced)
                .build();
    }
}