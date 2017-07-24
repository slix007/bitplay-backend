package com.bitplay.persistance.repository;

import com.bitplay.persistance.domain.DeltaParams;

import org.springframework.data.repository.CrudRepository;

/**
 * Created by Sergey Shurmin on 6/17/17.
 */
public interface DeltaParamsRepository extends CrudRepository<DeltaParams, String> {
    DeltaParams findFirstByDocumentId(Long id);
}
