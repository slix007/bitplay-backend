package com.bitplay.model;

import java.math.BigDecimal;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class AccountBalance {

    private final BigDecimal wallet;
    private final BigDecimal available;
    private final BigDecimal eMark;
    private final BigDecimal eLast;
    private final BigDecimal eBest;
    private final BigDecimal eAvg;
    private final BigDecimal margin;
    private final BigDecimal upl;
    private final BigDecimal rpl;
    private final BigDecimal riskRate;

    public static AccountBalance empty() {
        return new AccountBalance(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
