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
    @Transient
    private Integer toNextFix;

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
        BigDecimal currDevUsd = base.subtract(current).abs();
        return currDevUsd.subtract(maxDevUsd).signum() > 0;
    }

    public void setSettingsParts(LastPriceDeviation lpd) {
        this.bitmexMain = lpd.bitmexMain;
        this.okexMain = lpd.okexMain;
        this.maxDevUsd = lpd.maxDevUsd;
        this.delaySec = lpd.delaySec;
    }
}
