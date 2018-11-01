package com.bitplay.persistance.repository;

import com.bitplay.persistance.domain.fluent.FplayTrade;
import java.util.Date;
import java.util.stream.Stream;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Created by Sergey Shurmin on 12/20/17.
 */
public interface FplayTradeRepository extends MongoRepository<FplayTrade, Long> {

    Stream<FplayTrade> streamFplayTradeByStartTimestampBetween(Date from, Date to);

    FplayTrade findTopByOrderByIdDesc();

    default Long getLastId() {
        return findTopByOrderByIdDesc().getId();
    }
}
