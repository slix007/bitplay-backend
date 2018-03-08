package com.bitplay.arbitrage.dto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Created by Sergey Shurmin on 1/31/18.
 */
public class AvgPrice {
    private static final Logger logger = LoggerFactory.getLogger(AvgPrice.class);
    private static final Logger deltasLogger = LoggerFactory.getLogger("DELTAS_LOG");

    private final Map<String, AvgPriceItem> pItems = new LinkedHashMap<>();
    private final BigDecimal maxAmount;
    private final String marketName;

    private BigDecimal openPrice;

    public AvgPrice(BigDecimal maxAmount, String marketName) {
        this.maxAmount = maxAmount;
        this.marketName = marketName;
    }

    public String getMarketName() {
        return marketName;
    }

    public synchronized void setOpenPrice(BigDecimal openPrice) {
        this.openPrice = openPrice;
    }

    public synchronized void addPriceItem(String orderId, BigDecimal amount, BigDecimal price) {
        if (orderId != null && amount != null && price != null) {
            pItems.put(orderId, new AvgPriceItem(amount, price));
        } else {
            logger.info("failed addPriceItem: orderId=" + orderId + ", amount=" + amount + ", price" + price);
        }
    }

    public synchronized BigDecimal getOpenPrice() {
        return openPrice;
    }

    public synchronized BigDecimal getAvg() {
        return getAvg(false);
    }

    public synchronized BigDecimal getAvg(boolean withLogs) {
        BigDecimal avgPrice = BigDecimal.ZERO;
        try {
            if (pItems.isEmpty()) {
                if (withLogs) {
                    logger.warn(marketName + " WARNING avg price. Use openPrice: " + this);
                    deltasLogger.info(marketName + "AvgPrice by openPrice: " + openPrice);
                }
                return openPrice; // may be null
            }

            StringBuilder sb = new StringBuilder();
            //  (192 * 11550,00 + 82 * 11541,02) / (82 + 192) = 11547,31
            BigDecimal sumNumerator = pItems.values().stream()
                    .filter(Objects::nonNull)
                    .filter(avgPriceItem -> avgPriceItem.getAmount() != null && avgPriceItem.getPrice() != null)
                    .peek(avgPriceItem -> sb.append(String.format("(%s*%s)", avgPriceItem.amount, avgPriceItem.price)))
                    .reduce(BigDecimal.ZERO,
                            (accumulated, item) -> accumulated.add(item.getAmount().multiply(item.getPrice())),
                            BigDecimal::add);
            BigDecimal sumDenominator = pItems.values().stream()
                    .filter(Objects::nonNull)
                    .filter(avgPriceItem -> avgPriceItem.getAmount() != null && avgPriceItem.getPrice() != null)
                    .reduce(BigDecimal.ZERO,
                            (accumulated, item) -> accumulated.add(item.getAmount()),
                            BigDecimal::add);

            if (maxAmount.compareTo(sumDenominator) != 0) {
                logger.warn(marketName + " WARNING avg price calc: " + this.toString() + " NiceFormat: " + sb.toString());
//                final BigDecimal left = maxAmount.subtract(sumDenominator);
//                if (openPrice != null) {
//                    sumNumerator = sumNumerator.add(left.multiply(openPrice));
//                } else {
//                    // use left amount * last price
//                    final AvgPriceItem lastItem = (AvgPriceItem) pItems.entrySet().toArray()[pItems.size() - 1];
//                    sumNumerator = sumNumerator.add(left.multiply(lastItem.getPrice()));
//                }
//                sumDenominator = maxAmount;
            }

            avgPrice = sumDenominator.signum() == 0 ? BigDecimal.ZERO : sumNumerator.divide(sumDenominator, 2, RoundingMode.HALF_UP);

            if (withLogs) {
                deltasLogger.info(marketName + "AvgPrice: " + sb.toString() + " = " + avgPrice);
            }
        } catch (Exception e) {
            logger.error("Error on Avg", e);
        }
        return avgPrice;
    }

    public synchronized Map<String, AvgPriceItem> getpItems() {
        return new HashMap<>(pItems);
    }

    public synchronized boolean isItemsEmpty() {
        return pItems.size() == 0;
    }

    @Override
    public String toString() {
        return "AvgPrice{" +
                "pItems=" + pItems +
                ", maxAmount=" + maxAmount +
                ", openPrice=" + openPrice +
                ", marketName='" + marketName + '\'' +
                '}';
    }
}
