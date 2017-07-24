package com.bitplay.persistance.repository;

import com.bitplay.persistance.domain.GuiParams;

import org.springframework.data.repository.CrudRepository;

import java.math.BigInteger;

/**
 * Created by Sergey Shurmin on 6/17/17.
 */
public interface GuiParamsRepository extends CrudRepository<GuiParams, BigInteger> {
    GuiParams findFirstByDocumentId(Long id);
}
