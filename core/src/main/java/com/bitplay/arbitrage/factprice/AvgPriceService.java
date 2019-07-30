package com.bitplay.arbitrage.factprice;

import com.bitplay.arbitrage.dto.AvgPriceItem;
import com.bitplay.persistance.OrderRepositoryService;
import com.bitplay.persistance.domain.fluent.FplayOrder;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AvgPriceService {

    @Autowired
    private OrderRepositoryService orderRepositoryService;

    public Map<String, AvgPriceItem> getPItems(Long tradeId, Integer marketId) {
        final List<FplayOrder> allByTradeId = orderRepositoryService.findAll(tradeId, marketId);
        return allByTradeId.stream()
                .map(FplayOrder::getLimitOrder)
                .collect(Collectors.toMap(LimitOrder::getId,
                        o -> new AvgPriceItem(o.getCumulativeAmount(), o.getAveragePrice(), o.getStatus().toString())));
    }



}
