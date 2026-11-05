package com.pps.smartenglish.commerce.repository;

import com.pps.smartenglish.commerce.domain.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    boolean existsByProviderAndProviderRef(String provider, String providerRef);
}
