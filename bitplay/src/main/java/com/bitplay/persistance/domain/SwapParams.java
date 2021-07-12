package com.bitplay.persistance.domain;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Created by Sergey Shurmin on 7/18/17.
 */
@Document(collection="swapParamsCollection")
@TypeAlias("swapParams")
@Getter
@Setter
public class SwapParams extends MarketDocument {
    private Ver activeVersion;
    private SwapV2 swapV2;

    private String customSwapTime = "";
    private BigDecimal cumFundingRate = BigDecimal.ZERO;
    private BigDecimal cumFundingCost = BigDecimal.ZERO;
    private BigDecimal cumSwapProfit = BigDecimal.ZERO;
    private BigDecimal cumFee = BigDecimal.ZERO;
    private BigDecimal cumSpl = BigDecimal.ZERO;
    private BigDecimal cumSwapDiff = BigDecimal.ZERO;

    public static SwapParams createDefault() {
        final SwapParams swapParams = new SwapParams();
        swapParams.setSwapV2(SwapV2.createDefault());
        swapParams.setActiveVersion(Ver.OFF);
        return swapParams;
    }

    public void setSettingsParts(SwapParams swapParams) {
        this.activeVersion = swapParams.activeVersion;
        this.swapV2 = swapParams.swapV2;
        this.customSwapTime = swapParams.customSwapTime;
    }

    public enum Ver {OFF, V1, V2,}
}
