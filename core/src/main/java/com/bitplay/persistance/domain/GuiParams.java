package com.bitplay.persistance.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.util.Date;

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
public class GuiParams extends AbstractDocument {

    private BigDecimal border1 = BigDecimal.valueOf(10000);
    private BigDecimal border2 = BigDecimal.valueOf(10000);
    private BigDecimal makerDelta = BigDecimal.ZERO;
    private BigDecimal buValue = BigDecimal.ZERO;
    private BigDecimal cumDelta = BigDecimal.ZERO;
    private BigDecimal cumDeltaFact = BigDecimal.ZERO;
    private String lastDelta = null;
    private BigDecimal cumDiffFact1 = BigDecimal.ZERO;
    private BigDecimal cumDiffFact2 = BigDecimal.ZERO;
    private BigDecimal cumDiffFact = BigDecimal.ZERO;
    private BigDecimal cumDiffFactBr = BigDecimal.ZERO;
    private BigDecimal cumAstDiffFact1 = BigDecimal.ZERO;
    private BigDecimal cumAstDiffFact2 = BigDecimal.ZERO;
    private BigDecimal cumAstDiffFact = BigDecimal.ZERO;
    private BigDecimal diffFact = BigDecimal.ZERO;
    private BigDecimal com1 = BigDecimal.ZERO;
    private BigDecimal com2 = BigDecimal.ZERO;
    private BigDecimal bitmexMCom = BigDecimal.ZERO;
    private BigDecimal cumBitmexMCom = BigDecimal.ZERO;
    private BigDecimal cumCom1 = BigDecimal.ZERO;
    private BigDecimal cumCom2 = BigDecimal.ZERO;

    private BigDecimal astDelta1 = BigDecimal.ZERO;
    private BigDecimal astDelta2 = BigDecimal.ZERO;
    private BigDecimal cumAstDelta1 = BigDecimal.ZERO;
    private BigDecimal cumAstDelta2 = BigDecimal.ZERO;
    private BigDecimal astDeltaFact1 = BigDecimal.ZERO;
    private BigDecimal astDeltaFact2 = BigDecimal.ZERO;
    private BigDecimal cumAstDeltaFact1 = BigDecimal.ZERO;
    private BigDecimal cumAstDeltaFact2 = BigDecimal.ZERO;
    private BigDecimal astCom1 = BigDecimal.ZERO;
    private BigDecimal astCom2 = BigDecimal.ZERO;
    private BigDecimal astCom = BigDecimal.ZERO;
    private BigDecimal cumAstCom1 = BigDecimal.ZERO;
    private BigDecimal cumAstCom2 = BigDecimal.ZERO;
    private BigDecimal cumAstCom = BigDecimal.ZERO;
    private BigDecimal astBitmexMCom = BigDecimal.ZERO;
    private BigDecimal cumAstBitmexMCom = BigDecimal.ZERO;
    private BigDecimal slip = BigDecimal.ZERO;
    private BigDecimal slipBr = BigDecimal.ZERO;

    private int diffFactBrFailsCount = 0;
    private int counter1 = 0;
    private int completedCounter1 = 0;
    private int counter2 = 0;
    private int completedCounter2 = 0;
//    private BigDecimal block1 = BigDecimal.valueOf(100);
//    private BigDecimal block2 = BigDecimal.valueOf(1);
    private BigDecimal posBefore = BigDecimal.ZERO;
    private BigDecimal volPlan = BigDecimal.ZERO;
    private BigDecimal reserveBtc1 = BigDecimal.valueOf(0.00001);
    private BigDecimal reserveBtc2 = BigDecimal.valueOf(0.00001);
    private String okCoinOrderType = "maker";
    private BigDecimal hedgeAmount = BigDecimal.ZERO;
    private BigDecimal maxDiffCorr = BigDecimal.valueOf(1000);
    private Long periodToCorrection = 30L;
    private BigDecimal bMrLiq = BigDecimal.valueOf(75);
    private BigDecimal oMrLiq = BigDecimal.valueOf(20);
    private BigDecimal bDQLOpenMin = BigDecimal.valueOf(300);
    private BigDecimal oDQLOpenMin = BigDecimal.valueOf(350);
    private BigDecimal bDQLCloseMin = BigDecimal.valueOf(100);
    private BigDecimal oDQLCloseMin = BigDecimal.valueOf(150);
    private BigDecimal fundingRateFee = BigDecimal.ZERO;

    private Date lastOBChange;
    private Date lastCorrCheck;
    private Date lastMDCCheck;

}