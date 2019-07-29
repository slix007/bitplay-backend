package com.bitplay.persistance.repository;

import com.bitplay.persistance.domain.fluent.dealprices.DealPrices;
import org.springframework.data.repository.CrudRepository;

public interface DealPricesRepository extends CrudRepository<DealPrices, Long> {

    DealPrices findFirstByTradeId(Long tradeId);
}
