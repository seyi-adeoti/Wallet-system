package com.wallet.service;


import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.RedisTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.math.BigDecimal;
import com.wallet.dto.response.WalletLookupDTO;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String BALANCE_KEY   = "wallet:balance:";
    private static final String ACCOUNT_KEY   = "wallet:account:";
    private static final Duration BALANCE_TTL = Duration.ofSeconds(30); 
    private static final Duration ACCOUNT_TTL = Duration.ofHours(1);   

    // Cache wallet balance
    public void cacheBalance(UUID walletId, BigDecimal balance) {
        redisTemplate.opsForValue().set(
            BALANCE_KEY + walletId,
            balance.toString(),
            BALANCE_TTL
        );
    }

    // Get cached balance
    public Optional<BigDecimal> getCachedBalance(UUID walletId) {
        Object cached = redisTemplate.opsForValue().get(BALANCE_KEY + walletId);
        if (cached == null) return Optional.empty();
        return Optional.of(new BigDecimal(cached.toString()));
    }

    // Invalidate balance cache immediately after any transaction
    public void invalidateBalance(UUID walletId) {
        redisTemplate.delete(BALANCE_KEY + walletId);
        log.debug("Balance cache invalidated for wallet: {}", walletId);
    }

    // Cache account number lookup (account number → wallet info)
    public void cacheAccountLookup(String accountNumber, WalletLookupDTO dto) {
        redisTemplate.opsForValue().set(
            ACCOUNT_KEY + accountNumber,
            dto,
            ACCOUNT_TTL
        );
    }

    public Optional<WalletLookupDTO> getCachedAccount(String accountNumber) {
        Object cached = redisTemplate.opsForValue().get(ACCOUNT_KEY + accountNumber);
        if (cached == null) return Optional.empty();
        return Optional.of((WalletLookupDTO) cached);
    }
}