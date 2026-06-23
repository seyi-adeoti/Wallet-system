package com.wallet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.config.TestSecurityConfig;
import com.wallet.config.UserIdHeaderAuthFilter;
import com.wallet.entity.User;
import com.wallet.entity.Wallet;
import com.wallet.repository.UserRepository;
import com.wallet.repository.WalletRepository;
import com.wallet.repository.WalletTransactionRepository;
import com.wallet.service.WalletService;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestSecurityConfig.class, UserIdHeaderAuthFilter.class})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WalletFeatureIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(WalletFeatureIntegrationTest.class);
    private static EmbeddedPostgres embeddedPostgres;

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired WalletTransactionRepository transactionRepository;
    @Autowired WalletService walletService;

    private static UUID senderUserId;
    private static UUID receiverUserId;
    private static String receiverAccountNumber;

    @DynamicPropertySource
    static void registerPostgres(DynamicPropertyRegistry registry) throws IOException {
        embeddedPostgres = EmbeddedPostgres.builder().start();
        registry.add("spring.datasource.url", () -> embeddedPostgres.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        log.info("Embedded Postgres started at {}", embeddedPostgres.getJdbcUrl("postgres", "postgres"));
    }

    @AfterAll
    static void stopPostgres() throws IOException {
        if (embeddedPostgres != null) {
            embeddedPostgres.close();
            log.info("Embedded Postgres stopped");
        }
    }

    @Test
    @Order(1)
    @DisplayName("User has one wallet auto-created on registration")
    void registerUserAutoCreatesWallet() throws Exception {
        String email = "alice-" + UUID.randomUUID() + "@test.com";
        String payload = """
                {"fullName":"Alice Sender","email":"%s","phone":"08012345678","password":"secret123"}
                """.formatted(email);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        senderUserId = UUID.fromString(body.get("data").get("id").asText());
        log.info("[REGISTER] senderUserId={} response={}", senderUserId, body);

        Wallet wallet = walletRepository.findByUserId(senderUserId).orElseThrow();
        assertThat(wallet.getAccountNumber()).isNotBlank();
        assertThat(wallet.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        log.info("[WALLET] auto-created accountNumber={} balance={}", wallet.getAccountNumber(), wallet.getBalance());

        walletService.creditWallet(wallet, new BigDecimal("100000.00"), "FUND-ALICE", "Test funding", "FUNDING");
    }

    @Test
    @Order(2)
    @DisplayName("Register receiver user with wallet")
    void registerReceiver() throws Exception {
        String email = "bob-" + UUID.randomUUID() + "@test.com";
        String payload = """
                {"fullName":"Bob Receiver","email":"%s","phone":"08087654321","password":"secret123"}
                """.formatted(email);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        receiverUserId = UUID.fromString(body.get("data").get("id").asText());
        receiverAccountNumber = walletRepository.findByUserId(receiverUserId).orElseThrow().getAccountNumber();
        log.info("[REGISTER] receiverUserId={} accountNumber={}", receiverUserId, receiverAccountNumber);
    }

    @Test
    @Order(3)
    @DisplayName("Transfer creates audit trail with balance snapshots")
    void transferCreatesAuditTrail() throws Exception {
        String reference = "TXN-" + UUID.randomUUID();
        String payload = """
                {"receiverAccountNumber":"%s","amount":15000.00,"narration":"Test transfer","reference":"%s"}
                """.formatted(receiverAccountNumber, reference);

        MvcResult result = mockMvc.perform(post("/api/v1/wallet/transfer")
                        .header("X-User-Id", senderUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andReturn();

        log.info("[TRANSFER] response={}", result.getResponse().getContentAsString());

        MvcResult history = mockMvc.perform(get("/api/v1/wallet/transactions")
                        .header("X-User-Id", senderUserId.toString())
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode txns = objectMapper.readTree(history.getResponse().getContentAsString()).get("data");
        log.info("[AUDIT] sender transactions={}", txns);

        assertThat(txns).isNotEmpty();
        JsonNode debit = txns.get(0);
        assertThat(debit.get("balanceBefore").decimalValue()).isEqualByComparingTo(new BigDecimal("100000.00"));
        assertThat(debit.get("balanceAfter").decimalValue()).isEqualByComparingTo(new BigDecimal("85000.00"));
        assertThat(debit.get("reference").asText()).contains(reference);
    }

    @Test
    @Order(4)
    @DisplayName("Idempotency — same reference never processed twice")
    void idempotentTransferByReference() throws Exception {
        String reference = "IDEM-" + UUID.randomUUID();
        String payload = """
                {"receiverAccountNumber":"%s","amount":5000.00,"reference":"%s"}
                """.formatted(receiverAccountNumber, reference);

        mockMvc.perform(post("/api/v1/wallet/transfer")
                        .header("X-User-Id", senderUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        Wallet senderBefore = walletRepository.findByUserId(senderUserId).orElseThrow();
        BigDecimal balanceBeforeRetry = senderBefore.getBalance();
        log.info("[IDEMPOTENCY] balance before retry={}", balanceBeforeRetry);

        mockMvc.perform(post("/api/v1/wallet/transfer")
                        .header("X-User-Id", senderUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        Wallet senderAfter = walletRepository.findByUserId(senderUserId).orElseThrow();
        log.info("[IDEMPOTENCY] balance after retry={}", senderAfter.getBalance());
        assertThat(senderAfter.getBalance()).isEqualByComparingTo(balanceBeforeRetry);
    }

    @Test
    @Order(5)
    @DisplayName("CBN KYC tier limits enforced — single transfer limit")
    void kycSingleTransferLimitEnforced() throws Exception {
        String reference = "KYC-SINGLE-" + UUID.randomUUID();
        String payload = """
                {"receiverAccountNumber":"%s","amount":60000.00,"reference":"%s"}
                """.formatted(receiverAccountNumber, reference);

        MvcResult result = mockMvc.perform(post("/api/v1/wallet/transfer")
                        .header("X-User-Id", senderUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andReturn();

        log.info("[KYC] single limit rejection={}", result.getResponse().getContentAsString());
        assertThat(result.getResponse().getContentAsString()).contains("single transfer limit");
    }

    @Test
    @Order(6)
    @DisplayName("Get balance returns current wallet state")
    void getBalance() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/wallet/balance")
                        .header("X-User-Id", senderUserId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").exists())
                .andReturn();

        log.info("[BALANCE] {}", result.getResponse().getContentAsString());
    }

    @Test
    @Order(7)
    @DisplayName("Atomic debit+credit — insufficient funds rolls back entire transfer")
    void atomicTransferRollsBackOnInsufficientFunds() throws Exception {
        Wallet sender = walletRepository.findByUserId(senderUserId).orElseThrow();
        BigDecimal balanceBefore = sender.getBalance();
        String reference = "FAIL-" + UUID.randomUUID();
        String payload = """
                {"receiverAccountNumber":"%s","amount":85000.00,"reference":"%s"}
                """.formatted(receiverAccountNumber, reference);

        mockMvc.perform(post("/api/v1/wallet/transfer")
                        .header("X-User-Id", senderUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());

        Wallet senderAfter = walletRepository.findByUserId(senderUserId).orElseThrow();
        log.info("[ATOMIC] balance before={} after failed transfer={}", balanceBefore, senderAfter.getBalance());
        assertThat(senderAfter.getBalance()).isEqualByComparingTo(balanceBefore);
    }

    @Test
    @Order(8)
    @DisplayName("Concurrent transfers respect balance — no overdraft from race")
    void concurrentTransfersDoNotOverdraw() throws Exception {
        User concurrentUser = userRepository.save(User.builder()
                .fullName("Concurrent User")
                .email("concurrent-" + UUID.randomUUID() + "@test.com")
                .phone("08099998888")
                .passwordHash(new BCryptPasswordEncoder().encode("secret"))
                .build());
        Wallet wallet = walletService.createWallet(concurrentUser);
        walletService.creditWallet(wallet, new BigDecimal("10000.00"), "FUND-CONC", "Funding", "FUNDING");

        int threads = 5;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            pool.submit(() -> {
                try {
                    start.await();
                    String ref = "CONC-" + idx + "-" + UUID.randomUUID();
                    String payload = """
                            {"receiverAccountNumber":"%s","amount":3000.00,"reference":"%s"}
                            """.formatted(receiverAccountNumber, ref);
                    mockMvc.perform(post("/api/v1/wallet/transfer")
                                    .header("X-User-Id", concurrentUser.getId().toString())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(payload))
                            .andDo(r -> {
                                if (r.getResponse().getStatus() == 200) successes.incrementAndGet();
                                else failures.incrementAndGet();
                            });
                } catch (Exception e) {
                    failures.incrementAndGet();
                }
            });
        }

        start.countDown();
        pool.shutdown();
        while (!pool.isTerminated()) {
            Thread.sleep(100);
        }

        Wallet after = walletRepository.findByUserId(concurrentUser.getId()).orElseThrow();
        log.info("[CONCURRENT] successes={} failures={} finalBalance={}", successes.get(), failures.get(), after.getBalance());
        assertThat(after.getBalance()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(successes.get()).isLessThanOrEqualTo(3);
        assertThat(successes.get() + failures.get()).isEqualTo(threads);
    }
}
