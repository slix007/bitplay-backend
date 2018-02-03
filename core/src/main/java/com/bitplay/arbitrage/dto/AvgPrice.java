package com.bitplay.arbitrage.dto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Created by Sergey Shurmin on 1/31/18.
 */
public class AvgPrice {
    private static final Logger logger = LoggerFactory.getLogger(AvgPrice.class);
    private static final Logger tradeLogger = LoggerFactory.getLogger("BITMEX_TRADE_LOG");
    private static final Logger deltasLogger = LoggerFactory.getLogger("DELTAS_LOG");

    private final Map<String, AvgPriceItem> pItems = new LinkedHashMap<>();
    private BigDecimal maxAmount;
    private BigDecimal openPrice;
    private String marketName;

    public AvgPrice() {
    }

    public AvgPrice(BigDecimal maxAmount, String marketName) {
        this.maxAmount = maxAmount;
        this.marketName = marketName;
    }

    public void setOpenPrice(BigDecimal openPrice) {
        this.openPrice = openPrice;
    }

    public void addPriceItem(String orderId, BigDecimal amount, BigDecimal price) {
        if (orderId != null && amount != null && price != null) {
            pItems.put(orderId, new AvgPriceItem(amount, price));
        } else {
            logger.info("failed addPriceItem: orderId=" + orderId + ", amount=" + amount + ", price" + price);
        }
    }

    public BigDecimal getAvg() {
        if (pItems.isEmpty()) {
            if (!marketName.equals("bitmex")) {
                logger.warn(marketName + " WARNING avg price. Use openPrice: " + this);
            }
            deltasLogger.info("AvgPrice by openPrice: " + openPrice);
            return openPrice == null ? BigDecimal.ZERO : openPrice;
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

        if (marketName.equals("bitmex")) {
            deltasLogger.info("AvgPrice: " + sb.toString());
        }

        if (maxAmount.compareTo(sumDenominator) != 0) {
            logger.warn(marketName + " WARNING avg price calc: " + this);
            final BigDecimal left = maxAmount.subtract(sumDenominator);
            if (openPrice != null) {
                sumNumerator = sumNumerator.add(left.multiply(openPrice));
            } else {
                // use left amount * last price
                final AvgPriceItem lastItem = (AvgPriceItem) pItems.entrySet().toArray()[pItems.size() - 1];
                sumNumerator = sumNumerator.add(left.multiply(lastItem.getPrice()));
            }
            sumDenominator = maxAmount;
        }

        return sumNumerator.divide(sumDenominator, 2, RoundingMode.HALF_UP);
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
