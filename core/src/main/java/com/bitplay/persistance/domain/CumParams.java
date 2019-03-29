package com.bitplay.persistance.domain;

import java.math.BigDecimal;
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
@Document(collection = "guiParamsCollection")
@TypeAlias("CumParams")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class CumParams extends AbstractParams {

    private BigDecimal cumDelta = BigDecimal.ZERO;
    private BigDecimal cumDeltaFact = BigDecimal.ZERO;
    private BigDecimal cumDiffFact1 = BigDecimal.ZERO;
    private BigDecimal cumDiffFact2 = BigDecimal.ZERO;
    private BigDecimal cumDiffFact = BigDecimal.ZERO;
    private BigDecimal cumDiffFactBr = BigDecimal.ZERO;
    private BigDecimal cumDiff2Pre = BigDecimal.ZERO;
    private BigDecimal cumDiff2Post = BigDecimal.ZERO;
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

    private Integer diffFactBrFailsCount;
    //    private int counter1 = 0;
    private Integer completedCounter1;
    //    private int counter2 = 0;
    private Integer completedCounter2;


}