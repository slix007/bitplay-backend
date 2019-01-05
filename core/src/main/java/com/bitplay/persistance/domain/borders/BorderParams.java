package com.bitplay.persistance.domain.borders;

import com.bitplay.persistance.domain.AbstractDocument;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Created by Sergey Shurmin on 7/18/17.
 */
@Document(collection = "bordersCollection")
@TypeAlias("borders")
@Getter
@Setter
public class BorderParams extends AbstractDocument {

    private Ver activeVersion;
    private PosMode posMode;
    private BordersV1 bordersV1;
    private BordersV2 bordersV2;
    private Integer recalcPeriodSec;
    private Integer deltaMinFixPeriodSec;
    private BorderDelta borderDelta;
    private BigDecimal maxBorder;
    private Boolean onlyOpen;

    public BorderParams(Ver activeVersion, BordersV1 bordersV1, BordersV2 bordersV2) {
        this.setId(1L);
        this.activeVersion = activeVersion;
        this.posMode = PosMode.OK_MODE;
        this.bordersV1 = bordersV1;
        this.bordersV2 = bordersV2;
    }

    public enum Ver {V1, V2, OFF}

    public enum PosMode {BTM_MODE, OK_MODE}
}
