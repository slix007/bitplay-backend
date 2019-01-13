package com.bitplay.market;

import java.math.BigDecimal;

public interface LimitsService {

    boolean outsideLimitsForPreliq(BigDecimal currentPos);

}
