package com.bitplay.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class Leverage {

    private final BigDecimal leverage;
    private final String description;
}
