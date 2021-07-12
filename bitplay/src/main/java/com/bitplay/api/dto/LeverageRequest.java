package com.bitplay.api.dto;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class LeverageRequest {

    private BigDecimal leverage;
}
