package com.bitplay.arbitrage.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.knowm.xchange.dto.Order.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by Sergey Shurmin on 1/31/18.
 */
public class AvgPrice implements Serializable {
    private static final Logger logger = LoggerFactory.getLogger(AvgPrice.class);

    public final static String FAKE_ORDER_ID = "-1";

    private final String counterName;
    private final Map<String, AvgPriceItem> pItems = new LinkedHashMap<>();
    private final BigDecimal fullAmount;
    private final String marketName;
    private final int scale;

    private String deltaLogTmp;

    private BigDecimal openPrice;

    public AvgPrice(String counterName, BigDecimal fullAmount, String marketName, int scale) {
        this.counterName = counterName;
        this.fullAmount = fullAmount;
        this.marketName = marketName;
        this.scale = scale;
    }

    public int getScale() {
        return scale;
    }

    public String getMarketName() {
        return marketName;
    }

    public synchronized void setOpenPrice(BigDecimal openPrice) {
        this.openPrice = openPrice;
    }

    public synchronized void addPriceItem(String counterName, String orderId, BigDecimal amount, BigDecimal price, OrderStatus orderStatus) {
        String ordStatus = orderStatus == null ? "" : orderStatus.toString();
        addPriceItem(counterName, orderId, amount, price, ordStatus);
    }

    public synchronized void addPriceItem(String counterName, String orderId, BigDecimal amount, BigDecimal price, String ordStatus) {
        if (counterName != null && counterName.equals(this.counterName)) {
            addPriceItem(orderId, amount, price, ordStatus);
        }
    }

    /**
     * WARNING: no check with counterName.
     */
    private void addPriceItem(String orderId, BigDecimal amount, BigDecimal price, String ordStatus) {
        if (orderId != null) {
            pItems.put(orderId, new AvgPriceItem(amount, price, ordStatus));
        } else {
            logger.info("failed addPriceItem: orderId=null, amount=" + amount + ", price" + price);
        }
    }

    public synchronized BigDecimal getOpenPrice() {
        return openPrice;
    }

    public synchronized BigDecimal getAvg() {
        try {
            return getAvg(false, "", null);
        } catch (Exception e) {
            logger.error("Error on Avg", e);
        }
        return BigDecimal.ZERO;
    }

    public synchronized BigDecimal getAvg(boolean withLogs, String counterName, StringBuilder logBuilder) throws RoundIsNotDoneException {
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
            logger.warn(msg);
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

    public String getDeltaLogTmp() {
        return deltaLogTmp;
    }

    public synchronized Map<String, AvgPriceItem> getpItems() {
        return new HashMap<>(pItems);
    }

    public synchronized boolean isItemsEmpty() {
        return pItems.size() == 0;
    }

    public synchronized boolean isZeroOrder() {
        return pItems.containsKey(FAKE_ORDER_ID);
    }

    @Override
    public String toString() {
        return "AvgPrice{" +
                "counterName=" + counterName +
                ", pItems=" + pItems +
                ", fullAmount=" + fullAmount +
                ", openPrice=" + openPrice +
                ", marketName='" + marketName + "'" +
                ", hash='@" + Integer.toHexString(hashCode()) + "'" +
                '}';
    }
}
