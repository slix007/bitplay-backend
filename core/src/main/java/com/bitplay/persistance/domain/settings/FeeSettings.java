package com.bitplay.persistance.domain.settings;

import java.math.BigDecimal;
import lombok.ToString;

/**
 * Created by Sergey Shurmin on 2/28/18.
 */
@ToString
public class FeeSettings {

    //b_taker_com_rate - размер taker комиссии у Битмекса в % (сейчас 0.075)
    //b_maker_com_rate - размер maker комиссии у Битмекса в % (сейчас -0.025)
    //o_taker_com_rate - размер taker комиссии у Окоина в % (сейчас 0.015)
    //o_maker_com_rate - размер maker комиссии у Оккоина в % (сейчас 0.015)

    private BigDecimal bTakerComRate;
    private BigDecimal bMakerComRate;
    private BigDecimal oTakerComRate;
    private BigDecimal oMakerComRate;

    public static FeeSettings createDefault() {
        final FeeSettings f = new FeeSettings();
        f.bTakerComRate = new BigDecimal("0.075");
        f.bMakerComRate = new BigDecimal("-0.025");
        f.oTakerComRate = new BigDecimal("0.015");
        f.oMakerComRate = new BigDecimal("0.015");
        return f;
    }

    public BigDecimal getbTakerComRate() {
        return bTakerComRate;
    }

    public void setbTakerComRate(BigDecimal bTakerComRate) {
        this.bTakerComRate = bTakerComRate;
    }

    public BigDecimal getbMakerComRate() {
        return bMakerComRate;
    }

    public void setbMakerComRate(BigDecimal bMakerComRate) {
        this.bMakerComRate = bMakerComRate;
    }

    public BigDecimal getoTakerComRate() {
        return oTakerComRate;
    }

    public void setoTakerComRate(BigDecimal oTakerComRate) {
        this.oTakerComRate = oTakerComRate;
    }

    public BigDecimal getoMakerComRate() {
        return oMakerComRate;
    }

    public void setoMakerComRate(BigDecimal oMakerComRate) {
        this.oMakerComRate = oMakerComRate;
    }
}
