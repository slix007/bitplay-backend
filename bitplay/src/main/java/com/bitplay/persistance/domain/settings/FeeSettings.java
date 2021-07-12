package com.bitplay.persistance.domain.settings;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Created by Sergey Shurmin on 2/28/18.
 */
@Data
public class FeeSettings {

    //b_taker_com_rate - размер taker комиссии у Битмекса в % (сейчас 0.075)
    //b_maker_com_rate - размер maker комиссии у Битмекса в % (сейчас -0.025)
    //o_taker_com_rate - размер taker комиссии у Окоина в % (сейчас 0.015)
    //o_maker_com_rate - размер maker комиссии у Оккоина в % (сейчас 0.015)

    private BigDecimal leftTakerComRate;
    private BigDecimal leftMakerComRate;
    private BigDecimal rightTakerComRate;
    private BigDecimal rightMakerComRate;

    public static FeeSettings createDefault() {
        final FeeSettings f = new FeeSettings();
        f.leftTakerComRate = new BigDecimal("0.075");
        f.leftMakerComRate = new BigDecimal("-0.025");
        f.rightTakerComRate = new BigDecimal("0.015");
        f.rightMakerComRate = new BigDecimal("0.015");
        return f;
    }

}
