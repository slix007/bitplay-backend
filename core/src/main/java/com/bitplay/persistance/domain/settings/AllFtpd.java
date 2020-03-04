package com.bitplay.persistance.domain.settings;

import com.bitplay.arbitrage.dto.ArbType;
import lombok.Data;

/**
 * deviation of fake taker price
 */
@Data
public class AllFtpd {

    private OkexFtpd left;
    private OkexFtpd right;

    public static AllFtpd createDefaults() {
        final AllFtpd o = new AllFtpd();
        o.left = OkexFtpd.createDefaults();
        o.right = OkexFtpd.createDefaults();
        return o;
    }

    public OkexFtpd get(ArbType arbType) {
        if (arbType == ArbType.LEFT) {
            return left;
        }
        return right;
    }
}
