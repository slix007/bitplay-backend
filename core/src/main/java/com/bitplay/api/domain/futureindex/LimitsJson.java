package com.bitplay.api.domain.futureindex;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Created by Sergey Shurmin on 4/7/18.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LimitsJson {
    private BigDecimal limitPrice;
    private BigDecimal limitAsk;
    private BigDecimal limitBid;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private Boolean insideLimits;
    private Boolean ignoreLimits;
}
