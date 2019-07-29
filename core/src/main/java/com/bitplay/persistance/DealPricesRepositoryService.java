package com.bitplay.persistance;

import com.bitplay.persistance.domain.fluent.dealprices.DealPrices;
import com.bitplay.persistance.domain.settings.PlacingType;
import com.bitplay.persistance.repository.DealPricesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DealPricesRepositoryService {

    private final MongoOperations mongoOperation;
    private final DealPricesRepository dealPricesRepository;

    public DealPrices saveNew(DealPrices dealPrices) {
        return dealPricesRepository.save(dealPrices);
    }

    public DealPrices findByTradeId(Long tradeId) {
        return tradeId != null ? dealPricesRepository.findFirstByTradeId(tradeId) : null;
    }

    public void justSetVolatileMode(Long tradeId, PlacingType btmPlacingType, PlacingType okexPlacingType) {
        final Query query = new Query(Criteria.where("_id").is(tradeId));
        final Update update = new Update()
                .inc("version", 1)
                .set("btmPlacingType", btmPlacingType)
                .set("okexPlacingType", okexPlacingType);
        mongoOperation.updateFirst(query, update, DealPrices.class);
    }
}
