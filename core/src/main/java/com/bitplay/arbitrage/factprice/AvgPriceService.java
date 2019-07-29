package com.bitplay.arbitrage.factprice;

import com.bitplay.arbitrage.dto.AvgPrice;
import com.bitplay.arbitrage.dto.AvgPriceItem;
import com.bitplay.arbitrage.dto.RoundIsNotDoneException;
import com.bitplay.persistance.OrderRepositoryService;
import com.bitplay.persistance.domain.fluent.FplayOrder;
import com.bitplay.persistance.domain.fluent.dealprices.FactPrice;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

    public FactPrice getbPriceFact(Long tradeId, Integer marketId) {
        final List<FplayOrder> allByTradeId = orderRepositoryService.findAll(tradeId, marketId);
        for (FplayOrder fplayOrder : allByTradeId) {
                final LimitOrder ord = fplayOrder.getLimitOrder();
                avgPrice.addPriceItem(fplayOrder.getCounterName(), fplayOrder.getOrderId(),
                        ord.getCumulativeAmount(), ord.getAveragePrice(), ord.getStatus());
        }


        return null;
    }

    public synchronized BigDecimal calcAvg(
            Map<String, AvgPriceItem> pItems, String marketName, BigDecimal scale,
            boolean withLogs, String counterName, StringBuilder logBuilder) throws RoundIsNotDoneException {
        BigDecimal avgPrice;
        List<AvgPriceItem> notNullItems = pItems.values().stream()
                .filter(Objects::nonNull)
                .filter(avgPriceItem -> avgPriceItem.getAmount() != null && avgPriceItem.getPrice() != null)
                .collect(Collectors.toList());

        if (notNullItems.isEmpty()) {
            if (withLogs) {
                String msg = String.format("#%s %s WARNING: this is only openPrice. %s", counterName, marketName, this);
                throw new RoundIsNotDoneException(msg);
            }
            return openPrice; // may be null
        }

        if (withLogs && logBuilder != null) {
            logBuilder.append(String.format("#%s %s %s", counterName, marketName, this));
        }

        final StringBuilder sb = new StringBuilder();
        //  (192 * 11550,00 + 82 * 11541,02) / (82 + 192) = 11547,31
        BigDecimal sumNumerator = notNullItems.stream()
                .peek(avgPriceItem -> sb.append(String.format("(%s*%s)", avgPriceItem.amount, avgPriceItem.price)))
                .reduce(BigDecimal.ZERO,
                        (accumulated, item) -> accumulated.add(item.getAmount().multiply(item.getPrice())),
                        BigDecimal::add);
        BigDecimal sumDenominator = notNullItems.stream()
                .reduce(BigDecimal.ZERO,
                        (accumulated, item) -> accumulated.add(item.getAmount()),
                        BigDecimal::add);

        if (fullAmount.compareTo(sumDenominator) != 0) {
            String msg = String.format("#%s %s WARNING avg price calc: %s NiceFormat: %s", counterName, marketName, this, sb.toString());
            log.warn(msg);
            if (withLogs) {
                throw new RoundIsNotDoneException(msg);
            }
//                final BigDecimal left = fullAmount.subtract(sumDenominator);
//                if (openPrice != null) {
//                    sumNumerator = sumNumerator.add(left.multiply(openPrice));
//                } else {
//                    // use left amount * last price
//                    final AvgPriceItem lastItem = (AvgPriceItem) pItems.entrySet().toArray()[pItems.size() - 1];
//                    sumNumerator = sumNumerator.add(left.multiply(lastItem.getPrice()));
//                }
//                sumDenominator = fullAmount;
        }

        avgPrice = sumDenominator.signum() == 0 ? BigDecimal.ZERO : sumNumerator.divide(sumDenominator, scale, RoundingMode.HALF_UP);

        if (withLogs) {
            deltaLogTmp = String.format("#%s %sAvgPrice: %s = %s", counterName, marketName, sb.toString(), avgPrice);
        }

        return avgPrice;
    }

}
