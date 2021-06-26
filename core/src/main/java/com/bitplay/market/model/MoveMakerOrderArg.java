package com.bitplay.market.model;

import com.bitplay.persistance.domain.fluent.FplayOrder;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class MoveMakerOrderArg {

    private final FplayOrder fplayOrder;
    private final BigDecimal newPrice;
    private final Object[] reqMovingArgs;
    private final String obBestFive;
}
