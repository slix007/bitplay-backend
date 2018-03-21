package com.bitplay.persistance.repository;

import com.bitplay.persistance.domain.correction.CorrLogs;

import org.springframework.data.repository.CrudRepository;

import java.math.BigInteger;

/**
 * Created by Sergey Shurmin on 6/17/17.
 */
public interface CorrLogsRepository extends CrudRepository<CorrLogs, BigInteger> {
}
