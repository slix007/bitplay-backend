package com.bitplay.persistance.domain.settings;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class Dql {

    private BigDecimal leftMrLiq;
    private BigDecimal rightMrLiq;
    private BigDecimal leftDqlOpenMin;
    private BigDecimal rightDqlOpenMin;
    private BigDecimal leftDqlCloseMin;
    private BigDecimal rightDqlCloseMin;

    private BigDecimal dqlLevel;
    private BigDecimal leftDqlKillPos;
    private BigDecimal rightDqlKillPos;

    public BigDecimal getRightDqlKillPos() {
        return rightDqlKillPos != null ? rightDqlKillPos : BigDecimal.ZERO;
    }

    public BigDecimal getRightDqlOpenMin() {
        return rightDqlOpenMin != null ? rightDqlOpenMin : BigDecimal.ZERO;
    }

    public BigDecimal getRightDqlCloseMin() {
        return rightDqlCloseMin != null ? rightDqlCloseMin : BigDecimal.ZERO;
    }
}
