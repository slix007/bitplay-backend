package com.bitplay.persistance.domain;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "guiParamsCollection")
@TypeAlias("CumParams")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class CumParams extends AbstractParams {

    private CumType cumType;
    private CumTimeType cumTimeType;

    private BigDecimal cumDelta;
    private BigDecimal cumDeltaFact;
    private BigDecimal cumDiffFact1;
    private BigDecimal cumDiffFact2;
    private BigDecimal cumDiffFact;
    private BigDecimal cumDiffFactBr;
    private BigDecimal cumDiff2Pre;
    private BigDecimal cumDiff2Post;
    private BigDecimal cumAstDiffFact1;
    private BigDecimal cumAstDiffFact2;
    private BigDecimal cumAstDiffFact;
    private BigDecimal diffFact;
    private BigDecimal com1;
    private BigDecimal com2;
    private BigDecimal bitmexMCom;
    private BigDecimal cumBitmexMCom;
    private BigDecimal cumCom1;
    private BigDecimal cumCom2;

    private BigDecimal astDelta1;
    private BigDecimal astDelta2;
    private BigDecimal cumAstDelta1;
    private BigDecimal cumAstDelta2;
    private BigDecimal astDeltaFact1;
    private BigDecimal astDeltaFact2;
    private BigDecimal cumAstDeltaFact1;
    private BigDecimal cumAstDeltaFact2;
    private BigDecimal astCom1;
    private BigDecimal astCom2;
    private BigDecimal astCom;
    private BigDecimal cumAstCom1;
    private BigDecimal cumAstCom2;
    private BigDecimal cumAstCom;
    private BigDecimal astBitmexMCom;
    private BigDecimal cumAstBitmexMCom;
    private BigDecimal slip;
    private BigDecimal slipBr;

    private Integer diffFactBrFailsCount;
    private Integer vert1 = 0;
    private Integer completedVert1;
    private Integer vert2 = 0;
    private Integer completedVert2;
    private Integer unstartedVert1;
    private Integer unstartedVert2;

    public void setDefaults() {
        this.cumDelta = BigDecimal.ZERO;
        this.cumDeltaFact = BigDecimal.ZERO;
        this.cumDiffFact1 = BigDecimal.ZERO;
        this.cumDiffFact2 = BigDecimal.ZERO;
        this.cumDiffFact = BigDecimal.ZERO;
        this.cumDiffFactBr = BigDecimal.ZERO;
        this.cumDiff2Pre = BigDecimal.ZERO;
        this.cumDiff2Post = BigDecimal.ZERO;
        this.cumAstDiffFact1 = BigDecimal.ZERO;
        this.cumAstDiffFact2 = BigDecimal.ZERO;
        this.cumAstDiffFact = BigDecimal.ZERO;
        this.diffFact = BigDecimal.ZERO;
        this.com1 = BigDecimal.ZERO;
        this.com2 = BigDecimal.ZERO;
        this.bitmexMCom = BigDecimal.ZERO;
        this.cumBitmexMCom = BigDecimal.ZERO;
        this.cumCom1 = BigDecimal.ZERO;
        this.cumCom2 = BigDecimal.ZERO;

        this.astDelta1 = BigDecimal.ZERO;
        this.astDelta2 = BigDecimal.ZERO;
        this.cumAstDelta1 = BigDecimal.ZERO;
        this.cumAstDelta2 = BigDecimal.ZERO;
        this.astDeltaFact1 = BigDecimal.ZERO;
        this.astDeltaFact2 = BigDecimal.ZERO;
        this.cumAstDeltaFact1 = BigDecimal.ZERO;
        this.cumAstDeltaFact2 = BigDecimal.ZERO;
        this.astCom1 = BigDecimal.ZERO;
        this.astCom2 = BigDecimal.ZERO;
        this.astCom = BigDecimal.ZERO;
        this.cumAstCom1 = BigDecimal.ZERO;
        this.cumAstCom2 = BigDecimal.ZERO;
        this.cumAstCom = BigDecimal.ZERO;
        this.astBitmexMCom = BigDecimal.ZERO;
        this.cumAstBitmexMCom = BigDecimal.ZERO;
        this.slip = BigDecimal.ZERO;
        this.slipBr = BigDecimal.ZERO;

        this.diffFactBrFailsCount = 0;
        this.vert1 = 0;
        this.completedVert1 = 0;
        this.vert2 = 0;
        this.completedVert2 = 0;
        this.unstartedVert1 = 0;
        this.unstartedVert2 = 0;
    }

    public void update(CumParams update) {
        this.cumDelta = update.cumDelta != null ? update.cumDelta : this.cumDelta;
        this.cumDeltaFact = update.cumDeltaFact != null ? update.cumDeltaFact : this.cumDeltaFact;
        this.cumDiffFact1 = update.cumDiffFact1 != null ? update.cumDiffFact1 : this.cumDiffFact1;
        this.cumDiffFact2 = update.cumDiffFact2 != null ? update.cumDiffFact2 : this.cumDiffFact2;
        this.cumDiffFact = update.cumDiffFact != null ? update.cumDiffFact : this.cumDiffFact;
        this.cumDiffFactBr = update.cumDiffFactBr != null ? update.cumDiffFactBr : this.cumDiffFactBr;
        this.cumDiff2Pre = update.cumDiff2Pre != null ? update.cumDiff2Pre : this.cumDiff2Pre;
        this.cumDiff2Post = update.cumDiff2Post != null ? update.cumDiff2Post : this.cumDiff2Post;
        this.cumAstDiffFact1 = update.cumAstDiffFact1 != null ? update.cumAstDiffFact1 : this.cumAstDiffFact1;
        this.cumAstDiffFact2 = update.cumAstDiffFact2 != null ? update.cumAstDiffFact2 : this.cumAstDiffFact2;
        this.cumAstDiffFact = update.cumAstDiffFact != null ? update.cumAstDiffFact : this.cumAstDiffFact;
        this.diffFact = update.diffFact != null ? update.diffFact : this.diffFact;
        this.com1 = update.com1 != null ? update.com1 : this.com1;
        this.com2 = update.com2 != null ? update.com2 : this.com2;
        this.bitmexMCom = update.bitmexMCom != null ? update.bitmexMCom : this.bitmexMCom;
        this.cumBitmexMCom = update.cumBitmexMCom != null ? update.cumBitmexMCom : this.cumBitmexMCom;
        this.cumCom1 = update.cumCom1 != null ? update.cumCom1 : this.cumCom1;
        this.cumCom2 = update.cumCom2 != null ? update.cumCom2 : this.cumCom2;

        this.astDelta1 = update.astDelta1 != null ? update.astDelta1 : this.astDelta1;
        this.astDelta2 = update.astDelta2 != null ? update.astDelta2 : this.astDelta2;
        this.cumAstDelta1 = update.cumAstDelta1 != null ? update.cumAstDelta1 : this.cumAstDelta1;
        this.cumAstDelta2 = update.cumAstDelta2 != null ? update.cumAstDelta2 : this.cumAstDelta2;
        this.astDeltaFact1 = update.astDeltaFact1 != null ? update.astDeltaFact1 : this.astDeltaFact1;
        this.astDeltaFact2 = update.astDeltaFact2 != null ? update.astDeltaFact2 : this.astDeltaFact2;
        this.cumAstDeltaFact1 = update.cumAstDeltaFact1 != null ? update.cumAstDeltaFact1 : this.cumAstDeltaFact1;
        this.cumAstDeltaFact2 = update.cumAstDeltaFact2 != null ? update.cumAstDeltaFact2 : this.cumAstDeltaFact2;
        this.astCom1 = update.astCom1 != null ? update.astCom1 : this.astCom1;
        this.astCom2 = update.astCom2 != null ? update.astCom2 : this.astCom2;
        this.astCom = update.astCom != null ? update.astCom : this.astCom;
        this.cumAstCom1 = update.cumAstCom1 != null ? update.cumAstCom1 : this.cumAstCom1;
        this.cumAstCom2 = update.cumAstCom2 != null ? update.cumAstCom2 : this.cumAstCom2;
        this.cumAstCom = update.cumAstCom != null ? update.cumAstCom : this.cumAstCom;
        this.astBitmexMCom = update.astBitmexMCom != null ? update.astBitmexMCom : this.astBitmexMCom;
        this.cumAstBitmexMCom = update.cumAstBitmexMCom != null ? update.cumAstBitmexMCom : this.cumAstBitmexMCom;
        this.slip = update.slip != null ? update.slip : this.slip;
        this.slipBr = update.slipBr != null ? update.slipBr : this.slipBr;

        this.diffFactBrFailsCount = update.diffFactBrFailsCount != null ? update.diffFactBrFailsCount : this.diffFactBrFailsCount;
        this.vert1 = update.vert1 != null ? update.vert1 : this.vert1;
        this.completedVert1 = update.completedVert1 != null ? update.completedVert1 : this.completedVert1;
        this.vert2 = update.vert2 != null ? update.vert2 : this.vert2;
        this.completedVert2 = update.completedVert2 != null ? update.completedVert2 : this.completedVert2;
        this.unstartedVert1 = update.unstartedVert1 != null ? update.unstartedVert1 : this.unstartedVert1;
        this.unstartedVert2 = update.unstartedVert2 != null ? update.unstartedVert2 : this.unstartedVert2;
    }
}