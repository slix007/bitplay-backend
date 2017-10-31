package com.bitplay.persistance.repository;

import com.bitplay.persistance.domain.TimeCompareRange;

import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Created by Sergey Shurmin on 10/6/17.
 */
public interface TimeCompareRangeRepository extends MongoRepository<TimeCompareRange, Long> {

}
