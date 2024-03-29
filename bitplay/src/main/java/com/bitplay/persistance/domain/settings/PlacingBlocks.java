package com.bitplay.persistance.domain.settings;

import com.bitplay.market.bitmex.BitmexService;
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
    @Transient
    private boolean leftOkex = false;

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
        if (leftOkex) {
            return toOkexCont(fixedBlockUsd);
        }
        return toBitmexCont(fixedBlockUsd);
    }

    public BigDecimal getDynMaxBlockBitmex() {
        if (leftOkex) {
            return toOkexCont(dynMaxBlockUsd);
        }
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

    public static BigDecimal toBitmexContPure(BigDecimal usd, boolean isQuanto, BigDecimal cm, boolean leftOkex) {
        if (leftOkex) {
            return toOkexCont(usd, isQuanto);
        }

        if (isQuanto) {
            return usd.multiply(cm).divide(BigDecimal.valueOf(10), 0, RoundingMode.HALF_UP);
        }
        return scaleBitmexCont(usd.setScale(0, RoundingMode.HALF_UP));
    }

    public static BigDecimal toBitmexCont(BigDecimal usd, boolean isQuanto, BigDecimal cm) {
        BigDecimal okexCont = toOkexCont(usd, isQuanto);
        if (isQuanto) {
            return okexCont.multiply(cm).setScale(0, RoundingMode.HALF_UP);
        }
        return scaleBitmexCont(okexCont.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP));
    }

    // set_eu: OkexCONT = block_usd/10
    // set_bu: OkexCONT = block_usd/100
    private BigDecimal toOkexCont(BigDecimal usd) {
        return toOkexCont(usd, isEth);
    }

    public static BigDecimal toOkexCont(BigDecimal usd, boolean isQuanto) {
        if (isQuanto) {
            return usd.divide(BigDecimal.valueOf(10), 0, RoundingMode.HALF_UP);
        }
        return usd.divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
    }

    public static BigDecimal getOkexBlockByBitmexBlock(BigDecimal b_block, boolean isEth, BigDecimal cm) {
        final BigDecimal usd = bitmexContToUsd(b_block, isEth, cm);
        return toOkexCont(usd, isEth);
    }

    public static BigDecimal okexContToUsd(BigDecimal cont, boolean isQuanto) {
        if (isQuanto) {
            return cont.multiply(BigDecimal.valueOf(10));
        }
        return cont.multiply(BigDecimal.valueOf(100));
    }

    public static BigDecimal bitmexContToUsd(BigDecimal cont, boolean isQuanto, BigDecimal cm) {
        if (isQuanto) {
            return cont.multiply(BigDecimal.valueOf(10)).divide(cm, 0, RoundingMode.HALF_UP);
        }
        return scaleBitmexCont(cont.setScale(0, RoundingMode.HALF_UP));
    }

    public static BigDecimal scaleBitmexCont(BigDecimal cont) {
        final BigDecimal divide = cont.divide(BitmexService.LOT_SIZE, 0, BigDecimal.ROUND_HALF_UP);
        return divide.multiply(BitmexService.LOT_SIZE);
    }

    public enum Ver {FIXED, DYNAMIC,}
}
