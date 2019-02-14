package com.bitplay.persistance.domain;

import java.math.BigDecimal;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Created by Sergey Shurmin on 4/18/17.
 */
@Document(collection="guiParamsCollection")
@TypeAlias("guiParams")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder=true)
public class GuiParams extends AbstractParams {

    private BigDecimal border1 = BigDecimal.valueOf(10000);
    private BigDecimal border2 = BigDecimal.valueOf(10000);

    private int counter1 = 0;
    private int counter2 = 0;

    private BigDecimal reserveBtc1 = BigDecimal.valueOf(0.00001);
    private BigDecimal reserveBtc2 = BigDecimal.valueOf(0.00001);

    private BigDecimal maxDiffCorr = BigDecimal.valueOf(1000);
    private Long periodToCorrection = 30L;

    private BigDecimal fundingRateFee = BigDecimal.ZERO;

    private Date lastOBChange;
    private Date lastCorrCheck;
    private Date lastMDCCheck;

    public void setSettingsParts(GuiParams guiParams) {
        this.border1 = guiParams.border1;
        this.border2 = guiParams.border2;
        this.reserveBtc1 = guiParams.reserveBtc1;
        this.reserveBtc2 = guiParams.reserveBtc2;
        this.maxDiffCorr = guiParams.maxDiffCorr;
        this.periodToCorrection = guiParams.periodToCorrection;
        this.fundingRateFee = guiParams.fundingRateFee;
    }
}