package com.bitplay.persistance.domain.settings;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class Dql {

    private BigDecimal bMrLiq;
    private BigDecimal oMrLiq;
    private BigDecimal leftDqlOpenMin;
    private BigDecimal rightDqlOpenMin;
    private BigDecimal leftDqlCloseMin;
    private BigDecimal rightDqlCloseMin;

    private BigDecimal dqlLevel;
    private BigDecimal leftDqlKillPos;
    private BigDecimal rightDqlKillPos;
}
