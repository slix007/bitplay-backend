package com.bitplay.persistance.repository;

import com.bitplay.persistance.domain.LiqParams;

import org.springframework.data.repository.CrudRepository;

/**
 * Created by Sergey Shurmin on 6/17/17.
 */
public interface LiqParamsRepository extends CrudRepository<LiqParams, String> {
    LiqParams findFirstByMarketName(String marketName);
}
