package com.bitplay.persistance.domain.settings;

import lombok.Data;
import org.springframework.data.annotation.Transient;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

@Data
public class SettingsVolatileMode {

    private Set<Field> activeFields = new HashSet<>();
    @Transient
    private Field fieldToRemove;
    @Transient
    private Field fieldToAdd;

    private PlacingType leftPlacingType;
    private PlacingType rightPlacingType;
    private Integer signalDelayMs;
    private PlacingBlocks placingBlocks;
    private PosAdjustment posAdjustment; // - posAdjMin, posAdjMax - установка соответствубщих значений;
    private Boolean adjustByNtUsd;

    private BigDecimal borderCrossDepth;
    //- b_add_border: прибавление установленного значения к b_border (если выбрана V1 borders) или к бордерам в таблицах b_br_close, b_br_open (если выбрана V2 borders);
    //- o_add_border: прибавление установленного значения к o_border (если выбрана V1 borders) или к бордерам в таблицах o_br_close, o_br_open (если выбрана V2 borders);
    private BigDecimal bAddBorder;
    private BigDecimal oAddBorder;

    /**
     * 0 or negative - means infinite
     */
    private Integer volatileDurationSec;
    private Integer volatileDelayMs;


    private Integer corrMaxTotalCount;
    private Integer adjMaxTotalCount;

    private ArbScheme arbScheme;

    private ConBoPortions conBoPortions;

    private Prem prem;
    //TODO corr max total, corr max adj: общее количество коррекций/подгонок (0=off);

    public enum Field {
        bitmexPlacingType, //deprecated
        okexPlacingType,//deprecated
        leftPlacingType,
        rightPlacingType,
        signalDelayMs,
        placingBlocks,
        posAdjustment,
        adjustByNtUsd,
        corr_adj,
        arb_scheme,
        R_wait_L_portions_minNtUsdToStartOkex,
        R_wait_L_portions_maxPortionUsdOkex,
        /*deprecated*/
        conBoPortions_minNtUsdToStartOkex,
        conBoPortions_maxPortionUsdOkex,

        /*auto params for prem*/
        BCD_prem,
        L_add_border_prem,
        R_add_border_prem
    }

    public ConBoPortions getConBoPortions(ConBoPortions mainMode) {
        final ConBoPortions res = new ConBoPortions();
        if (getActiveFields().contains(Field.R_wait_L_portions_maxPortionUsdOkex)) {
            res.setMaxPortionUsdOkex(this.conBoPortions.getMaxPortionUsdOkex());
        } else {
            res.setMaxPortionUsdOkex(mainMode.getMaxPortionUsdOkex());
        }
        if (getActiveFields().contains(Field.R_wait_L_portions_minNtUsdToStartOkex)) {
            res.setMinNtUsdToStartOkex(this.conBoPortions.getMinNtUsdToStartOkex());
        } else {
            res.setMinNtUsdToStartOkex(mainMode.getMinNtUsdToStartOkex());
        }
        return res;
    }
}
