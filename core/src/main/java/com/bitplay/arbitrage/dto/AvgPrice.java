package com.bitplay.arbitrage.dto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Sergey Shurmin on 1/31/18.
 */
public class AvgPrice {
    private static final Logger logger = LoggerFactory.getLogger(AvgPrice.class);

    private final List<AvgPriceItem> pItems = new ArrayList<>();
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

    public void addPriceItem(BigDecimal amount, BigDecimal price) {
        pItems.add(new AvgPriceItem(amount, price));
    }

    public BigDecimal getAvg() {
        if (pItems.size() == 0 && !marketName.equals("bitmex")) {
            logger.warn(marketName + " WARNING avg price. Use openPrice: " + this);
            return openPrice;
        }

        //  (192 * 11550,00 + 82 * 11541,02) / (82 + 192) = 11547,31
        BigDecimal sumNumerator = pItems.stream().reduce(BigDecimal.ZERO,
                (accumulated, item) -> accumulated.add(item.getAmount().multiply(item.getPrice())),
                BigDecimal::add);
        BigDecimal sumDenominator = pItems.stream().reduce(BigDecimal.ZERO,
                (accumulated, item) -> accumulated.add(item.getAmount()),
                BigDecimal::add);

        if (maxAmount.compareTo(sumDenominator) != 0) {
            logger.warn(marketName + " WARNING avg price calc: " + this);
            final BigDecimal left = maxAmount.subtract(sumDenominator);
            if (openPrice != null) {
                sumNumerator = sumNumerator.add(left.multiply(openPrice));
            } else {
                // use left amount * last price
                sumNumerator = sumNumerator.add(left.multiply(pItems.get(pItems.size() - 1).getPrice()));
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
                '}';
    }
}
