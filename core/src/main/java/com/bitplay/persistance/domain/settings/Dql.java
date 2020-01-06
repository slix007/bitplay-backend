package com.bitplay.persistance.domain.settings;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Dql {

    private BigDecimal dqlLevel;
    private BigDecimal btmDqlKillPos;
    private BigDecimal okexDqlKillPos;
}
