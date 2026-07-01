package com.wallet.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.dto.request.RegisterRequest;
import com.wallet.dto.request.LoginRequest;
import com.wallet.dto.request.WalletTransferRequest;
import com.wallet.dto.request.InterbankTransferRequest;
import com.wallet.dto.request.KycUpgradeRequest;
import com.wallet.dto.request.ReviewFlagRequest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Wallet API - All Endpoints Integration Tests")
public class AllEndpointsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String authToken;
    private String testEmail;
    private String testPhoneNumber;

    @BeforeEach
    public void setUp() throws Exception {
        testEmail = "testuser" + System.currentTimeMillis() + "@example.com";
        testPhoneNumber = "08123456789";
    }

    // ==================== AUTH ENDPOINTS ====================

    @Test
    @DisplayName("POST /api/v1/auth/register - Should register a new user successfully")
    public void testRegisterUser_Success() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email(testEmail)
                .password("SecurePass123!")
                .phoneNumber(testPhoneNumber)
                .firstName("John")
                .lastName("Doe")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(testEmail))
                .andExpect(jsonPath("$.data.userId").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/v1/auth/register - Should reject duplicate email")
    public void testRegisterUser_DuplicateEmail() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email(testEmail)
                .password("SecurePass123!")
                .phoneNumber(testPhoneNumber)
                .firstName("John")
                .lastName("Doe")
                .build();

        // First registration
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Second registration with same email
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/auth/login - Should login user and return token")
    public void testLoginUser_Success() throws Exception {
        // Register first
        RegisterRequest registerRequest = RegisterRequest.builder()
                .email(testEmail)
                .password("SecurePass123!")
                .phoneNumber(testPhoneNumber)
                .firstName("John")
                .lastName("Doe")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        // Now login
        LoginRequest loginRequest = LoginRequest.builder()
                .email(testEmail)
                .password("SecurePass123!")
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andReturn();

        // Extract token for later use
        String response = result.getResponse().getContentAsString();
        authToken = objectMapper.readTree(response).get("data").get("token").asText();
    }

    @Test
    @DisplayName("POST /api/v1/auth/login - Should reject invalid credentials")
    public void testLoginUser_InvalidCredentials() throws Exception {
        LoginRequest loginRequest = LoginRequest.builder()
                .email("nonexistent@example.com")
                .password("WrongPassword123!")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    // ==================== WALLET ENDPOINTS ====================

    @Test
    @DisplayName("GET /api/v1/wallet/balance - Should return wallet balance for authenticated user")
    public void testGetBalance_Success() throws Exception {
        // Setup: Register and login
        setupUserAndGetToken();

        mockMvc.perform(get("/api/v1/wallet/balance")
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.balance").isNotEmpty())
                .andExpect(jsonPath("$.data.accountNumber").isNotEmpty());
    }

    @Test
    @DisplayName("GET /api/v1/wallet/balance - Should return 401 without authentication")
    public void testGetBalance_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/wallet/balance")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/wallet/transfer - Should transfer funds between wallets")
    public void testTransferFunds_Success() throws Exception {
        // Setup: Create two users
        setupUserAndGetToken();
        String user1Token = authToken;

        // Create second user
        testEmail = "testuser2" + System.currentTimeMillis() + "@example.com";
        setupUserAndGetToken();
        String user2Token = authToken;

        // Transfer from user1 to user2
        WalletTransferRequest transferRequest = WalletTransferRequest.builder()
                .recipientAccountNumber("recipient-account-number")
                .amount(5000L)
                .narration("Payment for services")
                .referenceId("REF-" + System.currentTimeMillis())
                .build();

        mockMvc.perform(post("/api/v1/wallet/transfer")
                .header("Authorization", "Bearer " + user1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(transferRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.transactionId").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("SUCCESSFUL"));
    }

    @Test
    @DisplayName("POST /api/v1/wallet/transfer - Should reject transfer with insufficient balance")
    public void testTransferFunds_InsufficientBalance() throws Exception {
        setupUserAndGetToken();

        WalletTransferRequest transferRequest = WalletTransferRequest.builder()
                .recipientAccountNumber("recipient-account-number")
                .amount(999999999L)
                .narration("Large transfer")
                .referenceId("REF-" + System.currentTimeMillis())
                .build();

        mockMvc.perform(post("/api/v1/wallet/transfer")
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(transferRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/wallet/transactions - Should return paginated transaction history")
    public void testGetTransactions_Success() throws Exception {
        setupUserAndGetToken();

        mockMvc.perform(get("/api/v1/wallet/transactions")
                .param("page", "0")
                .param("size", "20")
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    // ==================== NIP TRANSFER ENDPOINTS ====================

    @Test
    @DisplayName("GET /api/v1/transfer/name-enquiry - Should verify account name")
    public void testNameEnquiry_Success() throws Exception {
        mockMvc.perform(get("/api/v1/transfer/name-enquiry")
                .param("accountNumber", "1234567890")
                .param("bankCode", "001")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accountName").isNotEmpty())
                .andExpect(jsonPath("$.data.accountNumber").value("1234567890"));
    }

    @Test
    @DisplayName("GET /api/v1/transfer/name-enquiry - Should handle invalid account")
    public void testNameEnquiry_InvalidAccount() throws Exception {
        mockMvc.perform(get("/api/v1/transfer/name-enquiry")
                .param("accountNumber", "0000000000")
                .param("bankCode", "999")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/transfer/interbank - Should initiate interbank transfer")
    public void testInterbankTransfer_Success() throws Exception {
        setupUserAndGetToken();

        InterbankTransferRequest request = InterbankTransferRequest.builder()
                .recipientAccountNumber("1234567890")
                .recipientBankCode("001")
                .amount(10000L)
                .narration("Interbank transfer")
                .referenceId("REF-" + System.currentTimeMillis())
                .build();

        mockMvc.perform(post("/api/v1/transfer/interbank")
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.transferId").isNotEmpty());
    }

    // ==================== KYC ENDPOINTS ====================

    @Test
    @DisplayName("GET /api/v1/kyc/status - Should return KYC status and limits")
    public void testGetKycStatus_Success() throws Exception {
        setupUserAndGetToken();

        mockMvc.perform(get("/api/v1/kyc/status")
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tier").isNotEmpty())
                .andExpect(jsonPath("$.data.singleTransactionLimit").isNumber())
                .andExpect(jsonPath("$.data.dailyLimit").isNumber());
    }

    @Test
    @DisplayName("POST /api/v1/kyc/upgrade - Should submit KYC upgrade request")
    public void testKycUpgrade_Success() throws Exception {
        setupUserAndGetToken();

        KycUpgradeRequest request = KycUpgradeRequest.builder()
                .bvn("12345678901")
                .nin("12345678901234")
                .address("123 Test Street, Lagos")
                .city("Lagos")
                .state("Lagos State")
                .zipCode("100001")
                .build();

        mockMvc.perform(post("/api/v1/kyc/upgrade")
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"));
    }

    // ==================== ADMIN/COMPLIANCE ENDPOINTS ====================

    @Test
    @DisplayName("GET /api/v1/admin/aml/flags - Should return open AML flags with pagination")
    public void testGetAmlFlags_Success() throws Exception {
        mockMvc.perform(get("/api/v1/admin/aml/flags")
                .param("page", "0")
                .param("size", "20")
                .header("Authorization", "Bearer " + getAdminToken())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/aml/flags/{id}/review - Should review and resolve AML flag")
    public void testReviewAmlFlag_Success() throws Exception {
        ReviewFlagRequest request = ReviewFlagRequest.builder()
                .decision("APPROVED")
                .reviewedBy("admin@example.com")
                .notes("Transaction verified as legitimate")
                .build();

        // Assuming a valid flagId exists
        mockMvc.perform(patch("/api/v1/admin/aml/flags/550e8400-e29b-41d4-a716-446655440000/review")
                .header("Authorization", "Bearer " + getAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("APPROVED"));
    }

    @Test
    @DisplayName("GET /api/v1/admin/settlement/{date} - Should return settlement report")
    public void testGetSettlementReport_Success() throws Exception {
        String date = LocalDate.now().format(DateTimeFormatter.ISO_DATE);

        mockMvc.perform(get("/api/v1/admin/settlement/" + date)
                .header("Authorization", "Bearer " + getAdminToken())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(date))
                .andExpect(jsonPath("$.totalTransactions").isNumber());
    }

    @Test
    @DisplayName("GET /api/v1/admin/gl/trial-balance - Should return trial balance")
    public void testGetTrialBalance_Success() throws Exception {
        String date = LocalDate.now().format(DateTimeFormatter.ISO_DATE);

        mockMvc.perform(get("/api/v1/admin/gl/trial-balance")
                .param("date", date)
                .header("Authorization", "Bearer " + getAdminToken())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts").isArray())
                .andExpect(jsonPath("$.totalDebits").isNumber())
                .andExpect(jsonPath("$.totalCredits").isNumber());
    }

    @Test
    @DisplayName("GET /api/v1/admin/gl/account/{code}/balance - Should return GL account balance")
    public void testGetGlAccountBalance_Success() throws Exception {
        mockMvc.perform(get("/api/v1/admin/gl/account/1001/balance")
                .header("Authorization", "Bearer " + getAdminToken())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountCode").value("1001"))
                .andExpect(jsonPath("$.balance").isNumber());
    }

    // ==================== HELPER METHODS ====================

    private void setupUserAndGetToken() throws Exception {
        RegisterRequest registerRequest = RegisterRequest.builder()
                .email(testEmail)
                .password("SecurePass123!")
                .phoneNumber(testPhoneNumber)
                .firstName("John")
                .lastName("Doe")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        LoginRequest loginRequest = LoginRequest.builder()
                .email(testEmail)
                .password("SecurePass123!")
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        authToken = objectMapper.readTree(response).get("data").get("token").asText();
    }

    private String getAdminToken() throws Exception {
        // In a real scenario, use an existing admin user or create one
        // For testing, you might use a hardcoded admin token or mock it
        return "admin-token-placeholder";
    }
}
