package com.bitplay.api.dto.states;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@AllArgsConstructor
@NoArgsConstructor
@Getter
public class OkexFtpdJson {
    private BigDecimal bod; // best offer distance
    private BigDecimal bod_max; // best offer distance
    private BigDecimal bod_min; // best offer distance

}
