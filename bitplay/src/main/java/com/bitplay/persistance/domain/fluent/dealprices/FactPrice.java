package com.bitplay.persistance.domain.fluent.dealprices;

import com.bitplay.arbitrage.dto.AvgPriceItem;
import com.bitplay.arbitrage.dto.RoundIsNotDoneException;
import com.bitplay.xchange.dto.Order.OrderStatus;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Getter
public class FactPrice {

    public final static String FAKE_ORDER_ID = "-1";

    private final String marketNameWithType;
    private final BigDecimal fullAmount;
    private final int scale;

    public FactPrice(String marketNameWithType, BigDecimal fullAmount, int scale) {
        this.marketNameWithType = marketNameWithType;
        this.fullAmount = fullAmount;
        this.scale = scale;
    }

    private Map<String, AvgPriceItem> pItems = new HashMap<>();
    private String deltaLogTmp;
    private BigDecimal openPrice;

    public boolean isNotFinished() {
        final BigDecimal filled = getFilled();
        return filled.compareTo(fullAmount) < 0;
    }

    public synchronized BigDecimal getFilled() {
        return pItems.values().stream()
                    .map(AvgPriceItem::getAmount)
                    .reduce(BigDecimal::add)
                    .orElse(BigDecimal.ZERO);
    }

    public synchronized boolean isItemsEmpty() {
        return pItems.size() == 0;
    }

    public synchronized boolean isZeroOrder() {
        return pItems.containsKey(FAKE_ORDER_ID);
    }

    public void setFakeOrder(BigDecimal amount, BigDecimal price) {
        pItems.put(FAKE_ORDER_ID, new AvgPriceItem(amount, price, OrderStatus.FILLED.toString()));
    }

    public void setOpenPrice(BigDecimal openPrice) {
        this.openPrice = openPrice;
    }

    public void addPriceItem(String orderId, BigDecimal cumulativeAmount, BigDecimal averagePrice, OrderStatus status) {
        String ordStatus = status == null ? "" : status.toString();
        addPriceItem(orderId, cumulativeAmount, averagePrice, ordStatus);
    }

    public void addPriceItem(String orderId, BigDecimal amount, BigDecimal price, String ordStatus) {
        pItems.put(orderId, new AvgPriceItem(amount, price, ordStatus));
    }

    public BigDecimal getAvg() {
        try {
            return getAvg(false, "", null);
        } catch (Exception e) {
            log.error("Error on Avg", e);
        }
        return BigDecimal.ZERO;
    }

    public BigDecimal getAvg(boolean withLogs, String counterName, StringBuilder logBuilder) throws RoundIsNotDoneException {
        BigDecimal avgPrice;
        List<AvgPriceItem> notNullItems = pItems.values().stream()
                .filter(Objects::nonNull)
                .filter(avgPriceItem -> avgPriceItem.getAmount() != null && avgPriceItem.getPrice() != null)
                .collect(Collectors.toList());

        if (notNullItems.isEmpty()) {
            if (withLogs) {
                String msg = String.format("#%s %s WARNING: this is only openPrice. %s", counterName, marketNameWithType, this);
                throw new RoundIsNotDoneException(msg);
            }
            return openPrice; // may be null
        }

        if (withLogs && logBuilder != null) {
            logBuilder.append(String.format("#%s %s %s", counterName, marketNameWithType, this));
        }

        final StringBuilder sb = new StringBuilder();
        //  (192 * 11550,00 + 82 * 11541,02) / (82 + 192) = 11547,31
        BigDecimal sumNumerator = BigDecimal.ZERO;
        BigDecimal sumDenominator = BigDecimal.ZERO;
        for (AvgPriceItem item : notNullItems) {
            if (item.getAmount().signum() != 0 || item.getPrice().signum() != 0) {
                sb.append(String.format("(%s*%s)", item.getAmount(), item.getPrice()));
            }
            sumNumerator = sumNumerator.add(item.getAmount().multiply(item.getPrice()));
            sumDenominator = sumDenominator.add(item.getAmount());
        }

        if (fullAmount.compareTo(sumDenominator) != 0) {
            if (withLogs) {
                String msg = String.format("#%s %s WARNING avg price calc: %s NiceFormat: %s", counterName, marketNameWithType, this, sb.toString());
                log.warn(msg);
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
            deltaLogTmp = String.format("#%s %sAvgPrice: %s = %s", counterName, marketNameWithType, sb.toString(), avgPrice);
        }

        return avgPrice;
    }

    @Override
    public String toString() {
        return "AvgPrice{" +
                "pItems=" + pItems +
                ", fullAmount=" + fullAmount +
                ", openPrice=" + openPrice +
                ", marketNameWithType=" + marketNameWithType +
                ", scale=" + scale +
                '}';
    }
}
