package com.bitplay.persistance.repository;

import com.bitplay.persistance.domain.fluent.FplayTrade;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Created by Sergey Shurmin on 12/20/17.
 */
public interface FplayTradeRepository extends MongoRepository<FplayTrade, Long> {

}
