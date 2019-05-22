package com.bitplay.persistance.domain.settings;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import lombok.Data;
import org.springframework.data.annotation.Transient;

@Data
public class SettingsVolatileMode {

    private Set<Field> activeFields = new HashSet<>();
    @Transient
    private Field fieldToRemove;
    @Transient
    private Field fieldToAdd;

    private PlacingType bitmexPlacingType;
    private PlacingType okexPlacingType;
    private Integer signalDelayMs;
    private PlacingBlocks placingBlocks;
    private PosAdjustment posAdjustment; // - posAdjMin, posAdjMax - установка соответствубщих значений;
    private Boolean adjustByNtUsd;

    //- b_add_border: прибавление установленного значения к b_border (если выбрана V1 borders) или к бордерам в таблицах b_br_close, b_br_open (если выбрана V2 borders);
    //- o_add_border: прибавление установленного значения к o_border (если выбрана V1 borders) или к бордерам в таблицах o_br_close, o_br_open (если выбрана V2 borders);
    private BigDecimal bAddBorder;
    private BigDecimal oAddBorder;

    /**
     * 0 or negative - means infinite
     */
    private Integer volatileDurationSec;
    private Integer volatileDelayMs;

    private BigDecimal borderCrossDepth;

    private Integer corrMaxTotalCount;
    private Integer adjMaxTotalCount;

    private ArbScheme arbScheme;

    //TODO corr max total, corr max adj: общее количество коррекций/подгонок (0=off);

    public enum Field {
        bitmexPlacingType,
        okexPlacingType,
        signalDelayMs,
        placingBlocks,
        posAdjustment,
        adjustByNtUsd,
        corr_adj,
        arb_scheme
    }
}
