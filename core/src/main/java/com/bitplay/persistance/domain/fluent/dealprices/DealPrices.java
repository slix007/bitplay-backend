package com.bitplay.persistance.domain.fluent.dealprices;

import static com.bitplay.persistance.domain.borders.BorderParams.PosMode.LEFT_MODE;
import static com.bitplay.persistance.domain.borders.BorderParams.PosMode.RIGHT_MODE;

import com.bitplay.arbitrage.dto.BestQuotes;
import com.bitplay.market.MarketStaticData;
import com.bitplay.market.model.BtmFokAutoArgs;
import com.bitplay.persistance.domain.borders.BorderParams;
import com.bitplay.persistance.domain.borders.BorderParams.PosMode;
import com.bitplay.persistance.domain.fluent.DeltaName;
import com.bitplay.persistance.domain.settings.PlacingType;
import com.bitplay.persistance.domain.settings.TradingMode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * Created by Sergey Shurmin on 5/24/17.
 */
@Document(collection = "dealPricesSeries")
@TypeAlias("DealPrices")
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
@Getter
public class DealPrices {

    @Id
    private Long tradeId;
    @Version
    private Long version;
    @CreatedDate
    @Field
    @Indexed(expireAfterSeconds = 3600 * 24 * 31) // one month
    private LocalDateTime created;
    @LastModifiedDate
    private LocalDateTime updated;

    private MarketStaticData leftMarket;
    private MarketStaticData rightMarket;

    private List<BigDecimal> borderList = new ArrayList<>();
    private BigDecimal oBlock;
    private BigDecimal bBlock;
    private BigDecimal delta1Plan;
    private BigDecimal delta2Plan;
    private BigDecimal bPricePlan;
    private BigDecimal oPricePlan;
    private BigDecimal oPricePlanOnStart; // with CON_B_O, the plan and plan_start can be different.
    private FactPrice bPriceFact;
    private FactPrice oPriceFact; // = new AvgPrice("", BigDecimal.ZERO, "okex", 2);
    private DeltaName deltaName;
    private BestQuotes bestQuotes;
    private Integer pos_bo; // before order
    private Integer plan_pos_ao; // after order
    private BorderParams borderParamsOnStart;
    private BigDecimal border1;
    private BigDecimal border2;
    private PlacingType okexPlacingType;
    private PlacingType btmPlacingType;
    private TradingMode tradingMode;
    private String counterName;
    private BtmFokAutoArgs btmFokAutoArgs;
    private Boolean abortedSignal;
    private Boolean unstartedSignal;

    public BigDecimal getDelta1Fact() {
        final BigDecimal firstOpenPrice = bPriceFact.getAvg();
        final BigDecimal secondOpenPrice = oPriceFact.getAvg();
        return (firstOpenPrice != null && secondOpenPrice != null)
                ? firstOpenPrice.subtract(secondOpenPrice).setScale(getScale(), BigDecimal.ROUND_HALF_UP)
                : BigDecimal.ZERO;
    }

    public BigDecimal getDelta2Fact() {
        final BigDecimal firstOpenPrice = bPriceFact.getAvg();
        final BigDecimal secondOpenPrice = oPriceFact.getAvg();
        return (firstOpenPrice != null && secondOpenPrice != null)
                ? secondOpenPrice.subtract(firstOpenPrice).setScale(getScale(), BigDecimal.ROUND_HALF_UP)
                : BigDecimal.ZERO;
    }

    private int getScale() {
        return bPriceFact.getScale() > oPriceFact.getScale()
                ? bPriceFact.getScale()
                : oPriceFact.getScale();
    }

    public Details getDiffB() {
        if (bPricePlan == null || bPriceFact.getAvg() == null) {
            return new Details(BigDecimal.ZERO, "");
        }
        //ask1_p.subtract(thePrice);
        Details details;
        if (deltaName == DeltaName.O_DELTA) {
            final BigDecimal val = bPricePlan.subtract(bPriceFact.getAvg());
            details = new Details(val, String.format("diff_buy_b = ask_b[1](%s) - avg_price_buy_b(%s) = %s",
                    bPricePlan, bPriceFact.getAvg(), val));
        } else {
            final BigDecimal val = bPriceFact.getAvg().subtract(bPricePlan);
            details = new Details(val, String.format("diff_sell_b = avg_price_sell_b(%s) - bid_b[1](%s) = %s",
                    bPriceFact.getAvg(), bPricePlan, val));
        }
        return details;
    }

    public Details getDiffO() {
        if (oPricePlan == null || oPriceFact.getAvg() == null) {
            return new Details(BigDecimal.ZERO, "");
        }
        Details details;
        if (deltaName == DeltaName.O_DELTA) {
            final BigDecimal val = oPriceFact.getAvg().subtract(oPricePlan);
            details = new Details(val, String.format("diff_sell_o = avg_price_sell_o(%s) - bid_o[1](%s) = %s",
                    oPriceFact.getAvg(), oPricePlan, val));
        } else {
            final BigDecimal val = oPricePlan.subtract(oPriceFact.getAvg());
            details = new Details(val, String.format("diff_buy_o = ask_o[1](%s) - avg_price_buy_o(%s) = %s",
                    oPricePlan, oPriceFact.getAvg(), val));
        }
        return details;
    }

    public static class Details {

        final public BigDecimal val;
        final public String str;

        public Details(BigDecimal val, String str) {
            this.val = val;
            this.str = str;
        }
    }

    /**
     * The following should be set before:<br> BorderParams borderParamsOnStart, int pos_bo, DeltaName deltaName, BigDecimal b_block, BigDecimal o_block
     */
    public static int calcPlanAfterOrderPos(BigDecimal bBlock, BigDecimal oBlock, Integer pos_bo, PosMode posMode, DeltaName deltaName) {
        int pos_ao = pos_bo;
//        final PosMode posMode = borderParamsOnStart.getPosMode();
        if (posMode == LEFT_MODE) {
            if (deltaName == DeltaName.B_DELTA) {
                pos_ao = pos_bo - bBlock.intValue();
            } else {
                pos_ao = pos_bo + bBlock.intValue();
            }
        } else if (posMode == RIGHT_MODE) {
            if (deltaName == DeltaName.B_DELTA) {
                pos_ao = pos_bo + oBlock.intValue();
            } else {
                pos_ao = pos_bo - oBlock.intValue();
            }
        }
        return pos_ao;
    }
}
