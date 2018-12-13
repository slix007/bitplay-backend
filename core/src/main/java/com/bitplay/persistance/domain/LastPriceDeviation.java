package com.bitplay.persistance.domain;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Transient;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "guiParamsCollection")
@TypeAlias("lastPriceDeviation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class LastPriceDeviation extends AbstractParams {

    private BigDecimal bitmexMain;
    private BigDecimal okexMain;
    private BigDecimal maxDevUsd;
    private Integer delaySec;

    @Transient
    private BigDecimal bitmexMainCurr;
    @Transient
    private BigDecimal okexMainCurr;

    public boolean getBitmexMainExceed() {
        return isExceed(bitmexMain, bitmexMainCurr);
    }

    public boolean getOkexMainExceed() {
        return isExceed(okexMain, okexMainCurr);
    }

    private boolean isExceed(BigDecimal base, BigDecimal current) {
        if (base == null || current == null || maxDevUsd == null) {
            return false;
        }
        BigDecimal currDevUsd = bitmexMain.subtract(bitmexMainCurr).abs();
        return currDevUsd.subtract(maxDevUsd).signum() > 0;
    }

}
