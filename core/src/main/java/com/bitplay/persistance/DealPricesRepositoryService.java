package com.bitplay.persistance;

import com.bitplay.arbitrage.dto.AvgPriceItem;
import com.bitplay.persistance.domain.fluent.FplayOrder;
import com.bitplay.persistance.domain.fluent.dealprices.DealPrices;
import com.bitplay.persistance.domain.settings.PlacingType;
import com.bitplay.persistance.repository.DealPricesRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.dto.trade.LimitOrder;
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
    private final OrderRepositoryService orderRepositoryService;

    public DealPrices saveNew(DealPrices dealPrices) {
        dealPrices.setCreated(LocalDateTime.now());
        dealPrices.setUpdated(LocalDateTime.now());
        return dealPricesRepository.save(dealPrices);
    }

    public DealPrices findByTradeId(Long tradeId) {
        return tradeId != null ? dealPricesRepository.findFirstByTradeId(tradeId) : null;
    }

    public void justSetVolatileMode(Long tradeId, PlacingType btmPlacingType, PlacingType okexPlacingType) {
        final Query query = new Query(Criteria.where("_id").is(tradeId));
        final Update update = new Update()
                .inc("version", 1)
                .set("updated", LocalDateTime.now())
                .set("btmPlacingType", btmPlacingType)
                .set("okexPlacingType", okexPlacingType);
        mongoOperation.updateFirst(query, update, DealPrices.class);
    }

    public Map<String, AvgPriceItem> getPItems(Long tradeId, Integer marketId) {
        final List<FplayOrder> allByTradeId = orderRepositoryService.findAll(tradeId, marketId);
        return allByTradeId.stream()
                .map(FplayOrder::getLimitOrder)
                .collect(Collectors.toMap(LimitOrder::getId,
                        o -> new AvgPriceItem(o.getCumulativeAmount(), o.getAveragePrice(), o.getStatus().toString())));
    }


    public void update(DealPrices dealPrices) {
        dealPricesRepository.save(dealPrices);
    }
}
