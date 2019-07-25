package com.bitplay.persistance.repository;

import com.bitplay.persistance.domain.fluent.FplayOrder;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Created by Sergey Shurmin on 12/20/17.
 */
public interface OrderRepository extends MongoRepository<FplayOrder, String> {

    List<FplayOrder> findAllByTradeIdAndMarketId(Long tradeId, Integer marketId);
}
