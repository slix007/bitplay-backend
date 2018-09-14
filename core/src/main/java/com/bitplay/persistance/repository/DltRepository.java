package com.bitplay.persistance.repository;

import com.bitplay.persistance.domain.fluent.DeltaName;
import com.bitplay.persistance.domain.fluent.Dlt;
import java.util.Date;
import java.util.stream.Stream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;

/**
 * Created by Sergey Shurmin on 6/17/17.
 */
public interface DltRepository extends CrudRepository<Dlt, Long> {

    Stream<Dlt> streamDltByTimestampBetween(Date from, Date to);

    Stream<Dlt> streamDltByNameAndTimestampBetween(DeltaName deltaName, Date from, Date to);

    Page<Dlt> findByNameAndTimestampIsBefore(DeltaName deltaName, Date from, Pageable pageable);

    Page<Dlt> findByTimestampIsAfter(Date from, Pageable pageable);

    Page<Dlt> findByNameAndTimestampIsAfter(DeltaName deltaName, Date from, Pageable pageable);

    Dlt findFirstByNameOrderByTimestampDesc(DeltaName deltaName);
}
