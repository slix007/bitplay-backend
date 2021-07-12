package com.bitplay.market.model;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class BtmFokAutoArgs {

    private final BigDecimal delta;
    private final BigDecimal maxBorder;
    private final String allBorders;

}
