package com.wallet.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;
import com.wallet.service.NipService;
import com.wallet.dto.response.NameEnquiryResponse;
import com.wallet.dto.response.NipTransferResponse;
import com.wallet.dto.request.InterbankTransferRequest;
import jakarta.validation.Valid;
import java.util.UUID;



@RestController
@RequestMapping("/api/v1/transfer")
@RequiredArgsConstructor
public class NipController {

    private final NipService nipService;

    
    @GetMapping("/name-enquiry")
    public ResponseEntity<NameEnquiryResponse> nameEnquiry(
            @RequestParam String accountNumber,
            @RequestParam String bankCode) {
        return ResponseEntity.ok(nipService.nameEnquiry(accountNumber, bankCode));
    }

   
    @PostMapping("/interbank")
    public ResponseEntity<NipTransferResponse> interbankTransfer(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody InterbankTransferRequest request) {
        return ResponseEntity.ok(nipService.initiateTransfer(userId, request));
    }
}