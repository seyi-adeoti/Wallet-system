package com.wallet.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI walletServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("MFB Wallet Service API")
                        .description("""
                                ## Digital Banking & Wallet System
                                
                                A production-grade MFB wallet backend built with Java Spring Boot.
                                
                                ### Features
                                - User registration with KYC tier system (CBN compliant)
                                - Wallet creation and management
                                - Intra-wallet transfers with idempotency
                                - NIP interbank transfers with timeout handling
                                - AML transaction monitoring
                                - Double-entry General Ledger
                                - Daily settlement reports
                                
                                ### Authentication
                                All endpoints except `/api/v1/auth/**` require a Bearer JWT token.
                                
                                **How to authenticate:**
                                1. Register via `POST /api/v1/auth/register`
                                2. Login via `POST /api/v1/auth/login` to get your token
                                3. Click **Authorize** above and enter: `Bearer <your-token>`
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("MFB Engineering")
                                .email("engineering@mfbwallet.com"))
                        .license(new License()
                                .name("Private")
                                .url("https://mfbwallet.com")))

                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))

                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Enter your JWT token")))

                .externalDocs(new ExternalDocumentation()
                        .description("Engineering Wiki")
                        .url("https://wiki.mfbwallet.com"));
    }
}