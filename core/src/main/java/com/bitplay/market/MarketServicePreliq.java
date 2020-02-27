package com.bitplay.market;

import com.bitplay.arbitrage.dto.DelayTimer;
import com.bitplay.market.model.LiqInfo;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;

@Getter
public abstract class MarketServicePreliq extends MarketServicePortions {

    protected final PreliqService preliqService = new PreliqService(this);
    private final KillPosService killPosService = new KillPosService(this);
    protected LimitsService limitsService;

    public void setLimitsService(LimitsService limitsService) {
        this.limitsService = limitsService;
    }

    public LimitsService getLimitsService() {
        return limitsService;
    }

    public boolean noPreliq() {
        return preliqService.noPreliq();
    }

    public boolean isDqlOpenViolated() {
        LiqInfo liqInfo = getLiqInfo();
        final BigDecimal dqlOpenMin = preliqService.getDqlOpenMin();
        return isDqlViolated(liqInfo, dqlOpenMin);
    }

    public boolean isDqlViolated() {
        LiqInfo liqInfo = getLiqInfo();
        final BigDecimal dqlCloseMin = preliqService.getDqlCloseMin();
        return isDqlViolated(liqInfo, dqlCloseMin);
    }

    public boolean isDqlViolated(LiqInfo liqInfo, BigDecimal dqlCloseMin) {
        final BigDecimal dqlLevel = getPersistenceService().getSettingsRepositoryService().getSettings().getDql().getDqlLevel();
        final BigDecimal dqlCurr = liqInfo.getDqlCurr();
        return dqlCurr != null
                && dqlCurr.compareTo(dqlLevel) >= 0 // workaround when DQL is less zero
                && dqlCurr.compareTo(dqlCloseMin) <= 0;
    }

    public DelayTimer getDtPreliq() {
        return preliqService.getDtPreliq();
    }

    public DelayTimer getDtKillpos() {
        return preliqService.getDtKillpos();
    }

    public KillPosService getKillPosService() {
        return killPosService;
    }
}
