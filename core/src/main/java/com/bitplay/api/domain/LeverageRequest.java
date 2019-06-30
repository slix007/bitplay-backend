package com.bitplay.api.domain;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class LeverageRequest {

    private BigDecimal leverage;
}
