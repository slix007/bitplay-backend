package com.bitplay.persistance.domain.settings;

import lombok.Data;

import java.math.BigDecimal;

/**
 * deviation of fake taker price
 */
@Data
public class OkexFtpd {

    private BigDecimal okexFtpd; // usd or percent
    private BigDecimal okexFtpdBod; // best offer distance
    private OkexFtpdType okexFtpdType;

    public static OkexFtpd createDefaults() {
        final OkexFtpd o = new OkexFtpd();
        o.setOkexFtpd(BigDecimal.ZERO);
        o.setOkexFtpdBod(BigDecimal.ZERO);
        o.setOkexFtpdType(OkexFtpdType.PTS);
        return o;
    }

}
