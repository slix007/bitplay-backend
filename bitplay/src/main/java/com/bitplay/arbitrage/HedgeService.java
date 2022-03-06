package com.bitplay.arbitrage;

import com.bitplay.arbitrage.exceptions.NotYetInitializedException;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.domain.settings.Settings;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Data;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Setter
@Slf4j
public class HedgeService {

    @Autowired
    private SettingsRepositoryService settingsRepositoryService;

    @Autowired
    private ArbitrageService arbitrageService;

    private final AtomicReference<BigDecimal> hedgeBtc = new AtomicReference<>();
    private final AtomicReference<BigDecimal> hedgeBtcPrev = new AtomicReference<>();
    private final AtomicReference<BigDecimal> hedgeEth = new AtomicReference<>();
    private final AtomicReference<BigDecimal> hedgeEthPrev = new AtomicReference<>();

    // если активна опция auto для hedge:
    //hb_usd = - hedge_btc * usd_qu;
    //hedge_btc = b_ETHUSD_equ_best_btc + cold storage_btc; // внимательно: берется e_best у ETHUSD, a не XBTUSD Битмекса

    public BigDecimal getHedgeBtc() {
        final Settings settings = settingsRepositoryService.getSettings();
        final BigDecimal hedgeBtc = getHedgeBtcPure().multiply(settings.getHedgeCftBtc()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        final BigDecimal prev = hedgeBtcPrev.getAndSet(hedgeBtc);
        checkBigChange(prev, hedgeBtc, "btc", settings);
        return hedgeBtc;
    }

    public BigDecimal getHedgeBtcPure() {
        final Settings settings = settingsRepositoryService.getSettings();
        final BigDecimal current = this.hedgeBtc.get();
        final Boolean hedgeAuto = settings.getHedgeAuto();
        if (hedgeAuto && current == null) {
            throw new NotYetInitializedException();
        }
        return hedgeAuto ? current.negate() : settings.getHedgeBtc().negate();
    }

    public BigDecimal getHedgeEth() {
        final Settings settings = settingsRepositoryService.getSettings();
        final BigDecimal hedgeEth = getHedgeEthPure().multiply(settings.getHedgeCftEth()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        final BigDecimal prev = hedgeEthPrev.getAndSet(hedgeEth);
        checkBigChange(prev, hedgeEth, "eth", settings);
        return hedgeEth;
    }

    public BigDecimal getHedgeEthPure() {
        final Settings settings = settingsRepositoryService.getSettings();
        final Boolean hedgeAuto = settings.getHedgeAuto();
        final BigDecimal current = hedgeEth.get();
        if (hedgeAuto && current == null) {
            throw new NotYetInitializedException();
        }
        return hedgeAuto ? current.negate() : settings.getHedgeEth().negate();
    }

    public Hedge setHedge(Hedge hedge) {
        BigDecimal btc = hedge.btc;
        BigDecimal eth = hedge.eth;
        BigDecimal oldBtc = this.hedgeBtc.getAndSet(btc);
        BigDecimal oldEth =this.hedgeEth.getAndSet(eth);
        if (settingsRepositoryService.getSettings().getHedgeAuto()
                && (btc != null || eth != null)) {
            settingsRepositoryService.updateHedge(btc, eth);
        }
        return new Hedge(oldBtc, oldEth);
    }

    @Data
    public static class Hedge {
        final BigDecimal btc; final BigDecimal eth;
    }

    private static final BigDecimal ONE_HANDRED = BigDecimal.valueOf(100);
    private static final int MAX_PERCENT_WARN = 50;

    public boolean hasBigChange(Hedge oldHedge, Hedge newHedge) {
        if (oldHedge.btc == null || oldHedge.eth == null || newHedge.btc == null || newHedge.eth == null) {
            return false;
        }

        final boolean btcChanged;
        if (newHedge.btc.signum() == 0 || oldHedge.btc.signum() == 0) {final boolean bothZero = newHedge.btc.signum() == 0 && oldHedge.btc.signum() == 0;
            if (bothZero) btcChanged = false;
            else btcChanged = true;
        } else {
            int percentBtc = ONE_HANDRED.subtract(
                    ONE_HANDRED.multiply(newHedge.btc).divide(oldHedge.btc, 0, RoundingMode.HALF_UP)
            ).abs().intValue();
            btcChanged = percentBtc > MAX_PERCENT_WARN;
        }
        final boolean ethChanged;
        if (newHedge.eth.signum() == 0 || oldHedge.eth.signum() == 0) {
            final boolean bothZero = newHedge.eth.signum() == 0 && oldHedge.eth.signum() == 0;
            if (bothZero) ethChanged = false;
            else ethChanged = true;
        } else {
            int percentEth = ONE_HANDRED.subtract(
                    ONE_HANDRED.multiply(newHedge.eth).divide(oldHedge.eth, 0, RoundingMode.HALF_UP)
            ).abs().intValue();
            ethChanged = percentEth > MAX_PERCENT_WARN;
        }
        return btcChanged || ethChanged;
    }

    public void checkBigChange(BigDecimal prev, BigDecimal curr, String sym, Settings settings) {
        if (prev == null || curr == null || prev.signum() == 0) {
            return;
        }
        int percentEth = ONE_HANDRED.subtract(
                ONE_HANDRED.multiply(curr).divide(prev, 0, RoundingMode.HALF_UP)
        ).abs().intValue();
        if (percentEth > MAX_PERCENT_WARN) {
            final String fullMsg = String.format("HEDGE BIG CHANGE from %s(%s->%s).", sym, prev.negate(), curr.negate())
                    + " hedgeCftBtc=" + settings.getHedgeCftBtc()
                    + " hedgeCftEth=" + settings.getHedgeCftEth();
            log.warn(fullMsg);
            arbitrageService.printToCurrentDeltaLog(fullMsg);
        }
    }
}
