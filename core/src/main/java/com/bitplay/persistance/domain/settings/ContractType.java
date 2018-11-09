package com.bitplay.persistance.domain.settings;

import java.math.BigDecimal;

public interface ContractType {
    BigDecimal getTickSize();
    Integer getScale();

    boolean isEth();

    String toString();
}
