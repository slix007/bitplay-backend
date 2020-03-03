package com.bitplay.api.dto.ob;

import com.bitplay.model.SwapSettlement;
import lombok.Getter;
import lombok.Setter;

/**
 * Created by Sergey Shurmin on 4/14/17.
 */
@Getter
@Setter
public class FutureIndexJson {
    private String index;
    private String indexVal;
    private String timestamp;
    private LimitsJson limits;
    private ContractExtraJson contractExtraJson = new ContractExtraJson();

    // bitmex only
    private String fundingRate;
    private String fundingCost;
    private String position;
    private String swapTime;
    private String timeToSwap;
    private String swapType;
    private String timeCompareString;
    private String timeCompareUpdating;
    // okex only
    private String okexEstimatedDeliveryPrice;
    private SwapSettlement okexSwapSettlement;

    public static FutureIndexJson empty() {
        return new FutureIndexJson("", "", "", new LimitsJson(), null, null, null);
    }

    // okex
    public FutureIndexJson(String index, String indexVal, String timestamp, LimitsJson limits, String ethBtcBal,
                           String okexEstimatedDeliveryPrice, SwapSettlement okexSwapSettlement) {
        this.index = index;
        this.indexVal = indexVal;
        this.timestamp = timestamp;
        this.limits = limits;
        this.contractExtraJson.setEthBtcBal(ethBtcBal);

        // okex specific
        this.okexEstimatedDeliveryPrice = okexEstimatedDeliveryPrice;
        this.okexSwapSettlement = okexSwapSettlement;
    }

    // bitmex
    public FutureIndexJson(String index, String indexVal, String timestamp, String fundingRate,
                           String fundingCost,
                           String position, String swapTime, String timeToSwap, String swapType,
                           String timeCompareString, String timeCompareUpdating, LimitsJson limits, String bxbtBal) {
        this.index = index;
        this.indexVal = indexVal;
        this.timestamp = timestamp;
        this.limits = limits;
        this.contractExtraJson.setBxbtBal(bxbtBal);

        // bitmex specific
        this.fundingRate = fundingRate;
        this.fundingCost = fundingCost;
        this.position = position;
        this.swapTime = swapTime;
        this.timeToSwap = timeToSwap;
        this.swapType = swapType;
        this.timeCompareString = timeCompareString;
        this.timeCompareUpdating = timeCompareUpdating;
    }
}
