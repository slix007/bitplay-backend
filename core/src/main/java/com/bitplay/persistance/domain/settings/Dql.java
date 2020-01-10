package com.bitplay.persistance.domain.settings;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class Dql {

    private BigDecimal bMrLiq;
    private BigDecimal oMrLiq;
    private BigDecimal bDQLOpenMin;
    private BigDecimal oDQLOpenMin;
    private BigDecimal bDQLCloseMin;
    private BigDecimal oDQLCloseMin;

    private BigDecimal dqlLevel;
    private BigDecimal btmDqlKillPos;
    private BigDecimal okexDqlKillPos;
}
