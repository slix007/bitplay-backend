package com.bitplay.market;

import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.model.LiqInfo;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;

@Getter
@Slf4j
public abstract class MarketServicePreliq extends MarketServicePortions {

    public abstract LimitsService getLimitsService();

    public boolean noPreliq() {
        return extraCloseService.noPreliq();
    }

    public boolean isDqlOpenViolated() {
        LiqInfo liqInfo = getLiqInfo();
        final BigDecimal dqlOpenMin = getDqlOpenMin();
        return isDqlViolated(liqInfo, dqlOpenMin);
    }

    public boolean isDqlViolated() {
        LiqInfo liqInfo = getLiqInfo();
        final BigDecimal dqlCloseMin = getDqlCloseMin();
        return isDqlViolated(liqInfo, dqlCloseMin);
    }

    public boolean isDqlViolated(LiqInfo liqInfo, BigDecimal dqlCloseMin) {
        final BigDecimal dqlLevel = getPersistenceService().getSettingsRepositoryService().getSettings().getDql().getDqlLevel();
        return liqInfo.getDqlCurr() != null
                && liqInfo.getDqlCurr().compareTo(dqlLevel) >= 0 // workaround when DQL is less zero
                && liqInfo.getDqlCurr().compareTo(dqlCloseMin) <= 0;
    }

    private BigDecimal getDqlCloseMin() {
        if (getName().equals(BitmexService.NAME)) {
            return getPersistenceService().fetchGuiLiqParams().getBDQLCloseMin();
        }
        return getPersistenceService().fetchGuiLiqParams().getODQLCloseMin();
    }

    private BigDecimal getDqlOpenMin() {
        if (getName().equals(BitmexService.NAME)) {
            return getPersistenceService().fetchGuiLiqParams().getBDQLOpenMin();
        }
        return getPersistenceService().fetchGuiLiqParams().getODQLOpenMin();
    }

}
