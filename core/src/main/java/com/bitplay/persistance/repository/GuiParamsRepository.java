package com.bitplay.persistance.repository;

import com.bitplay.persistance.domain.AbstractParams;
import org.springframework.data.repository.CrudRepository;

/**
 * Created by Sergey Shurmin on 6/17/17.
 */
public interface GuiParamsRepository extends CrudRepository<AbstractParams, Long> {

}
