package com.bitplay.persistance.repository;

import com.bitplay.persistance.domain.Counters;

import org.springframework.data.repository.CrudRepository;

import java.math.BigInteger;

/**
 * Created by Sergey Shurmin on 6/17/17.
 */
public interface CountersRepository extends CrudRepository<Counters, BigInteger> {
    Counters findFirstByDocumentId(Long id);
}
