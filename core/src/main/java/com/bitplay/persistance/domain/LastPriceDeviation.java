package com.bitplay.persistance.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private BigDecimal bitmexExtra;
    private BigDecimal okexMain;
    private BigDecimal percentage;

    @Transient
    private BigDecimal bitmexMainCurr;
    @Transient
    private BigDecimal bitmexExtraCurr;
    @Transient
    private BigDecimal okexMainCurr;

    public boolean getBitmexMainExceed() {
        return isExceed(bitmexMain, bitmexMainCurr);
    }

    public boolean getBitmexExtraExceed() {
        return isExceed(bitmexExtra, bitmexExtraCurr);
    }

    public boolean getOkexMainExceed() {
        return isExceed(okexMain, okexMainCurr);
    }

    private boolean isExceed(BigDecimal base, BigDecimal current) {
        if (base == null || current == null || percentage == null) {
            return false;
        }
        // abs(curr/base*100 - 100) == persentage
        BigDecimal currPercentage = (
                (current.multiply(BigDecimal.valueOf(100)).divide(base, 2, RoundingMode.HALF_UP))
                        .subtract(BigDecimal.valueOf(100))
        ).abs();
        return currPercentage.subtract(percentage).signum() > 0;

    }

}
