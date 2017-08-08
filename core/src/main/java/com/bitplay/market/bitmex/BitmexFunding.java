package com.bitplay.market.bitmex;

import com.bitplay.arbitrage.SignalType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Created by Sergey Shurmin on 8/7/17.
 */
public class BitmexFunding {

    public static final BigDecimal MAX_F_RATE = BigDecimal.valueOf(0.0015);//BigDecimal.valueOf(0.15);

    private BigDecimal fundingRate;
    private OffsetDateTime updatingTime;
    private OffsetDateTime swapTime;
    private SignalType signalType;// SignalType.SWAP_CLOSE_SHORT or SignalType.SWAP_CLOSE_LONG or null
    private BigDecimal startPosition; // null when swap is not in progress.
    private OffsetDateTime startedSwapTime; // null when swap is not in progress

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
        return swapTime;
    }

    public void setSwapTime(OffsetDateTime swapTime) {
        this.swapTime = swapTime;
    }

    public SignalType getSignalType() {
        return signalType;
    }

    public void setSignalType(SignalType signalType) {
        this.signalType = signalType;
    }

    public BigDecimal getStartPosition() {
        return startPosition;
    }

    public void setStartPosition(BigDecimal startPosition) {
        this.startPosition = startPosition;
    }

    public OffsetDateTime getStartedSwapTime() {
        return startedSwapTime;
    }

    public void setStartedSwapTime(OffsetDateTime startedSwapTime) {
        this.startedSwapTime = startedSwapTime;
    }

    @Override
    public String toString() {
        return "BitmexFunding{" +
                "fundingRate=" + fundingRate +
                ", updatingTime=" + updatingTime +
                ", swapTime=" + swapTime +
                ", signalType=" + signalType +
                ", startPosition=" + startPosition +
                ", startedSwapTime=" + startedSwapTime +
                '}';
    }
}
