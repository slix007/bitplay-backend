package com.bitplay.arbitrage.dto;

import java.math.BigDecimal;

/**
 * Created by Sergey Shurmin on 1/31/18.
 */
public class AvgPriceItem {

    BigDecimal price;
    BigDecimal amount;

    public AvgPriceItem(BigDecimal amount, BigDecimal price) {
        this.amount = amount;
        this.price = price;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    @Override
    public String toString() {
        return "AvgPriceItem{" +
                "price=" + price +
                ", amount=" + amount +
                '}';
    }
}
