package com.bitplay.persistance.repository;

import com.bitplay.persistance.domain.SwapParams;

import org.springframework.data.repository.CrudRepository;

/**
 * Created by Sergey Shurmin on 6/17/17.
 */
public interface SwapParamsRepository extends CrudRepository<SwapParams, String> {
    SwapParams findFirstByMarketName(String marketName);
}
