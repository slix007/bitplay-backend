package com.bitplay.persistance.domain.fluent.dealprices;

import com.bitplay.arbitrage.dto.AvgPrice;
import com.bitplay.arbitrage.dto.AvgPriceItem;
import com.bitplay.arbitrage.dto.RoundIsNotDoneException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.knowm.xchange.dto.Order.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RequiredArgsConstructor
public class FactPrice {
    private static final Logger logger = LoggerFactory.getLogger(AvgPrice.class);

    public final static String FAKE_ORDER_ID = "-1";

    private final Map<String, AvgPriceItem> pItems = new LinkedHashMap<>();
    private final BigDecimal fullAmount;
    private final Integer marketId;
    private final int scale;

    private String deltaLogTmp;

    private BigDecimal openPrice;

    public int getScale() {
        return scale;
    }


    public synchronized boolean isNotFinished() {
        final BigDecimal filled = pItems.values().stream()
                .map(AvgPriceItem::getAmount)
                .reduce(BigDecimal::add)
                .orElse(BigDecimal.ZERO);
        return filled.compareTo(fullAmount) < 0;
    }

    public synchronized boolean isItemsEmpty() {
        return pItems.size() == 0;
    }

    public synchronized boolean isZeroOrder() {
        return pItems.containsKey(FAKE_ORDER_ID);
    }

}
