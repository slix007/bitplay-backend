package com.bitplay.persistance.repository;

import com.bitplay.persistance.domain.ExchangePair;
import com.bitplay.persistance.domain.correction.CorrParams;
import org.springframework.data.repository.CrudRepository;

/**
 * Created by Sergey Shurmin on 6/17/17.
 */
public interface CorrParamsRepository extends CrudRepository<CorrParams, Long> {
    CorrParams findFirstByExchangePair(ExchangePair exchange);
}
