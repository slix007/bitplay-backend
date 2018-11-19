package com.bitplay.persistance.domain.settings;

import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.Data;
import org.springframework.data.annotation.Transient;

/**
 * Created by Sergey Shurmin on 12/29/17.
 */
@Data
public class PlacingBlocks {

    private Ver activeVersion;
    private BigDecimal fixedBlockUsd;
    private BigDecimal dynMaxBlockUsd;

    /**
     * CM - correlation multiplier.
     */
    @Transient
    private BigDecimal cm = BigDecimal.valueOf(100);
    @Transient
    private boolean isEth = false;

    public static PlacingBlocks createDefault() {
        final PlacingBlocks placingBlocks = new PlacingBlocks();
        placingBlocks.setActiveVersion(Ver.FIXED);
        placingBlocks.setFixedBlockUsd(BigDecimal.valueOf(100));
        placingBlocks.setDynMaxBlockUsd(BigDecimal.valueOf(100));
        return placingBlocks;
    }

    // ТЗ:
    //Для всех set_bu:
    //100 USD block = 100 BitmexCONT & 1 OkexCONT;
    //Для всех set_eu:
    //10 USD block = CM BitmexCONT & 1 OkexCONT.

    public BigDecimal getFixedBlockBitmex() {
        return toBitmexCont(fixedBlockUsd);
    }

    public BigDecimal getDynMaxBlockBitmex() {
        return toBitmexCont(dynMaxBlockUsd);
    }

    public BigDecimal getFixedBlockOkex() {
        return toOkexCont(fixedBlockUsd);
    }

    public BigDecimal getDynMaxBlockOkex() {
        return toOkexCont(dynMaxBlockUsd);
    }

    // set_eu: BitmexCONT = block_usd*cm/10
    // set_bu: BitmexCONT = block_usd
    private BigDecimal toBitmexCont(BigDecimal usd) {
        return toBitmexCont(usd, isEth, cm);
    }

    public static BigDecimal toBitmexCont(BigDecimal usd, boolean isEth, BigDecimal cm) {
        if (isEth) {
            return usd.multiply(cm).divide(BigDecimal.valueOf(10), 0, RoundingMode.HALF_UP);
        }
        return usd.setScale(0, RoundingMode.HALF_UP);
    }

    // set_eu: OkexCONT = block_usd/10
    // set_bu: OkexCONT = block_usd/100
    private BigDecimal toOkexCont(BigDecimal usd) {
        if (isEth) {
            return usd.divide(BigDecimal.valueOf(10), 0, RoundingMode.HALF_UP);
        }
        return usd.divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
    }

    public enum Ver {FIXED, DYNAMIC,}
}
