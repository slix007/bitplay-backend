package com.bitplay.arbitrage;

import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.domain.settings.Settings;
import java.math.BigDecimal;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Setter
public class HedgeService {

    @Autowired
    private SettingsRepositoryService settingsRepositoryService;

    private BigDecimal hedgeBtc;
    private BigDecimal hedgeEth;

    // если активна опция auto для hedge:
    //hb_usd = - hedge_btc * usd_qu;
    //hedge_btc = b_ETHUSD_equ_best_btc + cold storage_btc; // внимательно: берется e_best у ETHUSD, a не XBTUSD Битмекса


    public BigDecimal getHedgeBtc() {
        final Settings settings = settingsRepositoryService.getSettings();
        return settings.getHedgeAuto() ? hedgeBtc.negate() : settings.getHedgeBtc();
    }

    public BigDecimal getHedgeEth() {
        final Settings settings = settingsRepositoryService.getSettings();
        return settings.getHedgeAuto() ? hedgeEth.negate() : settings.getHedgeEth();
    }
}
