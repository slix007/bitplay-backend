package com.bitplay.market.bitmex;

import com.bitplay.arbitrage.SignalType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Created by Sergey Shurmin on 8/7/17.
 */
public class BitmexFunding {

    public static final BigDecimal MAX_F_RATE = BigDecimal.valueOf(0.15);

    private BigDecimal fundingRate;
    private OffsetDateTime updatingTime;
    private OffsetDateTime fundingTimestamp;
    private SignalType signalType;// SignalType.SWAP_CLOSE_SHORT or SignalType.SWAP_CLOSE_LONG or null

    public BigDecimal getFundingRate() {
        return fundingRate;
    }

    public void setFundingRate(BigDecimal fundingRate) {
        this.fundingRate = fundingRate;
    }

    public OffsetDateTime getUpdatingTime() {
        return updatingTime;
    }

    public void setUpdatingTime(OffsetDateTime updatingTime) {
        this.updatingTime = updatingTime;
    }

    public OffsetDateTime getSwapTime() {
        return fundingTimestamp;
    }

    public void setFundingTimestamp(OffsetDateTime fundingTimestamp) {
        this.fundingTimestamp = fundingTimestamp;
    }

    public SignalType getSignalType() {
        return signalType;
    }

    public void setSignalType(SignalType signalType) {
        this.signalType = signalType;
    }
}
