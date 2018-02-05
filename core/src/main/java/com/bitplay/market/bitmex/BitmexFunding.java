package com.bitplay.market.bitmex;

import com.bitplay.arbitrage.dto.SignalType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * <p>
 * It is updated by swapTickerTimer.
 * </p>
 *
 * The latest info from market is in {@link info.bitrich.xchangestream.bitmex.dto.BitmexContractIndex},
 *
 * <p>Created by Sergey Shurmin on 8/7/17.</p>
 */
public class BitmexFunding {

    public static final BigDecimal MAX_F_RATE = BigDecimal.valueOf(0.0); //0.15

    // Fluid params on UI
    private BigDecimal fundingRate;     // from market
    private BigDecimal fundingCost;     // recalc each time from market
    private OffsetDateTime swapTime;    // from market
    private OffsetDateTime updatingTime;// ticker time

    // When swap in progress:
    private SignalType signalType;          // SignalType.SWAP_CLOSE_SHORT or SignalType.SWAP_CLOSE_LONG or null
    private BigDecimal startPosition;       // null when swap is not in progress. How much contracts we've closed to open it after.
    private OffsetDateTime fixedSwapTime;   // null when swap is not in progress. Keep old swapTime until we finish swap iteration.

    // for logs
    private BigDecimal swapClosePrice;
    private BigDecimal swapOpenPrice;

    public BigDecimal getFundingRate() {
        return fundingRate;
    }

    public void setFundingRate(BigDecimal fundingRate) {
        this.fundingRate = fundingRate;
    }

    public BigDecimal getFundingCost() {
        return fundingCost;
    }

    public void setFundingCost(BigDecimal fundingCost) {
        this.fundingCost = fundingCost;
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

    public OffsetDateTime getFixedSwapTime() {
        return fixedSwapTime;
    }

    public void setFixedSwapTime(OffsetDateTime fixedSwapTime) {
        this.fixedSwapTime = fixedSwapTime;
    }

    public BigDecimal getSwapClosePrice() {
        return swapClosePrice;
    }

    public void setSwapClosePrice(BigDecimal swapClosePrice) {
        this.swapClosePrice = swapClosePrice;
    }

    public BigDecimal getSwapOpenPrice() {
        return swapOpenPrice;
    }

    public void setSwapOpenPrice(BigDecimal swapOpenPrice) {
        this.swapOpenPrice = swapOpenPrice;
    }

    @Override
    public String toString() {
        return "BitmexFunding{" +
                "fundingRate=" + fundingRate +
                ", updatingTime=" + updatingTime +
                ", swapTime=" + swapTime +
                ", signalType=" + signalType +
                ", startPosition=" + startPosition +
                ", fixedSwapTime=" + fixedSwapTime +
                ", swapClosePrice=" + swapClosePrice +
                ", swapOpenPrice=" + swapOpenPrice +
                '}';
    }
}
