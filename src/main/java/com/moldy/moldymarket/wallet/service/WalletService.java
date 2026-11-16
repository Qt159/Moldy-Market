package com.moldy.moldymarket.wallet.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.config.CurrentUser;
import com.moldy.moldymarket.user.repository.UserRepository;
import com.moldy.moldymarket.wallet.dto.response.WalletResponse;
import com.moldy.moldymarket.wallet.entity.Wallet;
import com.moldy.moldymarket.wallet.mapper.WalletMapper;
import com.moldy.moldymarket.wallet.repository.WalletRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WalletService {
    WalletRepository walletRepository;
    WalletMapper walletMapper;
    UserRepository userRepository;
    CurrentUser currentUser;

    //Whenever users are created, their wallet is also created
    public void createWalletForUser(UUID userId){
        if(walletRepository.findByUserId(userId).isPresent())
            throw new AppException(ErrorCode.WALLET_EXISTED);

        Wallet wallet = new Wallet();
        wallet.setUserId(userId);
        try{
            walletMapper.toWalletResponse(walletRepository.save(wallet));
        }catch (DataIntegrityViolationException ex){
            throw new AppException(ErrorCode.WALLET_EXISTED);
        }
    }

    @Transactional
    public void ensureWalletForUser(UUID userId) {
        if (walletRepository.findByUserId(userId).isPresent()) return;
        userRepository.findByIdForWalletCreation(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        if (walletRepository.findByUserId(userId).isEmpty()) {
            Wallet wallet = new Wallet();
            wallet.setUserId(userId);
            walletRepository.save(wallet);
        }
    }

    @Transactional(readOnly = true)
    public WalletResponse getWalletByUserId(UUID userId){
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean admin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
        if (!admin && !userId.equals(currentUser.findCurrentUser().getId())) {
            throw new AppException(ErrorCode.WALLET_ACCESS_DENIED);
        }
        var wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_EXISTED));
        return walletMapper.toWalletResponse(wallet);

    }

}
