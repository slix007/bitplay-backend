package com.bitplay.arbitrage.dto;

import static com.bitplay.persistance.domain.borders.BorderParams.PosMode.BTM_MODE;
import static com.bitplay.persistance.domain.borders.BorderParams.PosMode.OK_MODE;

import com.bitplay.market.model.PlacingType;
import com.bitplay.persistance.domain.borders.BorderParams;
import com.bitplay.persistance.domain.borders.BorderParams.PosMode;
import com.bitplay.persistance.domain.fluent.DeltaName;
import com.bitplay.persistance.domain.settings.TradingMode;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Created by Sergey Shurmin on 5/24/17.
 */
@Getter
@Setter
@ToString
public class DealPrices implements Serializable {

    private List<BigDecimal> borderList = new ArrayList<>();
    private BigDecimal oBlock = BigDecimal.ZERO;
    private BigDecimal bBlock = BigDecimal.ZERO;
    private BigDecimal delta1Plan = BigDecimal.ZERO;
    private BigDecimal delta2Plan = BigDecimal.ZERO;
    private BigDecimal bPricePlan = BigDecimal.ZERO;
    private BigDecimal oPricePlan = BigDecimal.ZERO;
    private BigDecimal oPricePlanOnStart = BigDecimal.ZERO; // with CON_B_O, the plan and plan_start can be different.
    private AvgPrice bPriceFact = new AvgPrice("", BigDecimal.ZERO, "bitmex", 2);
    private AvgPrice oPriceFact = new AvgPrice("", BigDecimal.ZERO, "okex", 2);
    private DeltaName deltaName = DeltaName.B_DELTA;
    private BestQuotes bestQuotes;
    private Integer pos_bo; // before order
    private Integer plan_pos_ao; // after order
    private BorderParams borderParamsOnStart;
    private BigDecimal border1;
    private BigDecimal border2;
    private PlacingType okexPlacingType;
    private PlacingType btmPlacingType;
    private TradingMode tradingMode;
    private Long tradeId;

    public synchronized BigDecimal getBorder1() {
        return border1;
    }

    public void setBorder1(BigDecimal border1) {
        this.border1 = border1;
    }

    public synchronized BigDecimal getBorder2() {
        return border2;
    }

    public void setBorder2(BigDecimal border2) {
        this.border2 = border2;
    }

    public synchronized BorderParams getBorderParamsOnStart() {
        return borderParamsOnStart;
    }

    public void setBorderParamsOnStart(BorderParams borderParamsOnStart) {
        this.borderParamsOnStart = borderParamsOnStart;
    }

    /**
     * The following should be set before:<br> BorderParams borderParamsOnStart, int pos_bo, DeltaName deltaName, BigDecimal b_block, BigDecimal o_block
     */
    public void calcPlanPosAo() {
        this.plan_pos_ao = calcPlanAfterOrderPos(bBlock, oBlock);
    }

    public void calcPlanPosAo(BigDecimal b_block_input, BigDecimal o_block_input) {
        this.plan_pos_ao = calcPlanAfterOrderPos(b_block_input, o_block_input);
    }

    public synchronized void setDeltaName(DeltaName deltaName) {
        this.deltaName = deltaName;
    }

    public synchronized DeltaName getDeltaName() {
        return deltaName;
    }

    public synchronized void setSecondOpenPrice(BigDecimal secondOpenPrice) {
        final BigDecimal sop = secondOpenPrice.setScale(2, BigDecimal.ROUND_HALF_UP);
        oPriceFact.setOpenPrice(sop);
    }

    public synchronized BigDecimal getDelta1Fact() {
        final BigDecimal firstOpenPrice = bPriceFact.getAvg();
        final BigDecimal secondOpenPrice = oPriceFact.getAvg();
        return (firstOpenPrice != null && secondOpenPrice != null)
                ? firstOpenPrice.subtract(secondOpenPrice).setScale(getScale(), BigDecimal.ROUND_HALF_UP)
                : BigDecimal.ZERO;
    }

    public synchronized BigDecimal getDelta2Fact() {
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

    public synchronized Details getDiffB() {
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

    public synchronized Details getDiffO() {
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

    public synchronized BigDecimal getoBlock() {
        return oBlock;
    }

    public synchronized void setoBlock(BigDecimal oBlock) {
        this.oBlock = oBlock;
    }

    public synchronized BigDecimal getbBlock() {
        return bBlock;
    }

    public synchronized void setbBlock(BigDecimal bBlock) {
        this.bBlock = bBlock;
    }

    public synchronized BigDecimal getDelta1Plan() {
        return delta1Plan;
    }

    public synchronized void setDelta1Plan(BigDecimal delta1Plan) {
        this.delta1Plan = delta1Plan;
    }

    public synchronized BigDecimal getDelta2Plan() {
        return delta2Plan;
    }

    public synchronized void setDelta2Plan(BigDecimal delta2Plan) {
        this.delta2Plan = delta2Plan;
    }

    public synchronized BigDecimal getbPricePlan() {
        return bPricePlan;
    }

    public synchronized void setbPricePlan(BigDecimal bPricePlan) {
        this.bPricePlan = bPricePlan;
    }

    public synchronized BigDecimal getoPricePlan() {
        return oPricePlan;
    }

    public synchronized void setoPricePlan(BigDecimal oPricePlan) {
        this.oPricePlan = oPricePlan;
    }

    public synchronized AvgPrice getbPriceFact() {
        return bPriceFact;
    }

    public synchronized void setbPriceFact(AvgPrice bPriceFact) {
        this.bPriceFact = bPriceFact;
    }

    public synchronized AvgPrice getoPriceFact() {
        return oPriceFact;
    }

    public synchronized void setoPriceFact(AvgPrice oPriceFact) {
        this.oPriceFact = oPriceFact;
    }

    public BestQuotes getBestQuotes() {
        return bestQuotes;
    }

    public void setBestQuotes(BestQuotes bestQuotes) {
        this.bestQuotes = bestQuotes;
    }

    public synchronized Integer getPos_bo() {
        return pos_bo;
    }

    public synchronized void setPos_bo(Integer pos_bo) {
        this.pos_bo = pos_bo;
    }

    public synchronized Integer getPlan_pos_ao() {
        return plan_pos_ao;
    }

    public synchronized void setPlan_pos_ao(Integer plan_pos_ao) {
        this.plan_pos_ao = plan_pos_ao;
    }

    public synchronized BigDecimal getoPricePlanOnStart() {
        return oPricePlanOnStart;
    }

    public synchronized void setoPricePlanOnStart(BigDecimal oPricePlanOnStart) {
        this.oPricePlanOnStart = oPricePlanOnStart;
    }

    public static class Details {

        final public BigDecimal val;
        final public String str;

        public Details(BigDecimal val, String str) {
            this.val = val;
            this.str = str;
        }
    }

    private int calcPlanAfterOrderPos(BigDecimal bBlock, BigDecimal oBlock) {
        int pos_ao = pos_bo;
        final PosMode posMode = borderParamsOnStart.getPosMode();
        if (posMode == BTM_MODE) {
            if (deltaName == DeltaName.B_DELTA) {
                pos_ao = pos_bo - bBlock.intValue();
            } else {
                pos_ao = pos_bo + bBlock.intValue();
            }
        } else if (posMode == OK_MODE) {
            if (deltaName == DeltaName.B_DELTA) {
                pos_ao = pos_bo + oBlock.intValue();
            } else {
                pos_ao = pos_bo - oBlock.intValue();
            }
        }
        return pos_ao;
    }
}
