package com.bitplay.persistance.repository;

import com.bitplay.persistance.domain.TimeCompareParams;

import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Created by Sergey Shurmin on 10/6/17.
 */
public interface TimeCompareParamsRepository extends MongoRepository<TimeCompareParams, Long> {

}
