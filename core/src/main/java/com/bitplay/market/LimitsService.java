package com.bitplay.market;

import com.bitplay.api.domain.ob.LimitsJson;
import java.math.BigDecimal;

public interface LimitsService {

    boolean outsideLimitsForPreliq(BigDecimal currentPos);

    LimitsJson getLimitsJson();

}
