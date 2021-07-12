package com.bitplay.arbitrage;

import com.bitplay.arbitrage.exceptions.NotYetInitializedException;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.domain.settings.Settings;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@Setter
public class HedgeService {

    @Autowired
    private SettingsRepositoryService settingsRepositoryService;

    private volatile BigDecimal hedgeBtc;
    private volatile BigDecimal hedgeEth;

    // если активна опция auto для hedge:
    //hb_usd = - hedge_btc * usd_qu;
    //hedge_btc = b_ETHUSD_equ_best_btc + cold storage_btc; // внимательно: берется e_best у ETHUSD, a не XBTUSD Битмекса


    public BigDecimal getHedgeBtc() {
        final Settings settings = settingsRepositoryService.getSettings();
        if (settings.getHedgeAuto() && hedgeBtc == null) {
            throw new NotYetInitializedException();
        }
        return settings.getHedgeAuto() ? hedgeBtc.negate() : settings.getHedgeBtc().negate();
    }

    public BigDecimal getHedgeEth() {
        final Settings settings = settingsRepositoryService.getSettings();
        if (settings.getHedgeAuto() && hedgeBtc == null) {
            throw new NotYetInitializedException();
        }
        return settings.getHedgeAuto() ? hedgeEth.negate() : settings.getHedgeEth().negate();
    }

    public void setHedgeBtc(BigDecimal hedge) {
        this.hedgeBtc = hedge;
        final Settings settings = settingsRepositoryService.getSettings();
        if (settings.getHedgeAuto() && this.hedgeBtc != null) {
            // save settings
            settingsRepositoryService.updateHedge(this.hedgeBtc, null);
        }
    }

    public void setHedgeEth(BigDecimal hedge) {
        this.hedgeEth = hedge;
        final Settings settings = settingsRepositoryService.getSettings();
        if (settings.getHedgeAuto() && this.hedgeEth != null) {
            // save settings
            settingsRepositoryService.updateHedge(null, hedgeEth);
        }
    }
}
