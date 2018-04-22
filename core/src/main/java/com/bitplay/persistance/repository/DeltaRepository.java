package com.bitplay.persistance.repository;

import com.bitplay.persistance.domain.fluent.Delta;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;

import java.util.Date;
import java.util.stream.Stream;

/**
 * Created by Sergey Shurmin on 6/17/17.
 */
public interface DeltaRepository extends CrudRepository<Delta, Long> {

    Stream<Delta> streamDeltasByTimestampBetween(Date from, Date to);

    Page<Delta> findByTimestampIsAfter(Date from, Pageable pageable);

    Delta findFirstByOrderByTimestampDesc();
}
