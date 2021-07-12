package com.bitplay.arbitrage.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Created by Sergey Shurmin on 1/31/18.
 */
@Getter
@AllArgsConstructor
public class AvgPriceItem implements Serializable {

    BigDecimal amount;
    BigDecimal price;
    String ordStatus;

    @Override
    public String toString() {
        return "{" +
                "a=" + amount +
                ",p=" + price +
                ",s='" + ordStatus + '\'' +
                '}';
    }
}
