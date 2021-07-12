package com.bitplay.market.model;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Created by Sergey Shurmin on 2/25/18.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Affordable {
    private volatile BigDecimal forShort = BigDecimal.ZERO;
    private volatile BigDecimal forLong = BigDecimal.ZERO;

    @Override
    public String toString() {
        return "Affordable{" +
                "lg=" + forLong +
                ", st=" + forShort +
                '}';
    }
}
