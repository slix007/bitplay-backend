package com.bitplay.model;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
public class EstimatedPrice {
    private final BigDecimal price;
    private final Date timestamp;
}
