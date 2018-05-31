package com.bitplay.arbitrage.dto;

import com.bitplay.persistance.domain.borders.BorderParams;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.ToString;

/**
 * Created by Sergey Shurmin on 5/24/17.
 */
@ToString
public class DealPrices {
    private List<BigDecimal> borderList = new ArrayList<>();
    private BigDecimal oBlock = BigDecimal.ZERO;
    private BigDecimal bBlock = BigDecimal.ZERO;
    private BigDecimal delta1Plan = BigDecimal.ZERO;
    private BigDecimal delta2Plan = BigDecimal.ZERO;
    private BigDecimal bPricePlan = BigDecimal.ZERO;
    private BigDecimal oPricePlan = BigDecimal.ZERO;
    private AvgPrice bPriceFact = new AvgPrice(BigDecimal.ZERO, "bitmex");
    private AvgPrice oPriceFact = new AvgPrice(BigDecimal.ZERO, "okex");
    private DeltaName deltaName = DeltaName.B_DELTA;
    private BestQuotes bestQuotes;
    private Integer pos_bo;
    private Integer plan_pos_ao;
    private BorderParams borderParamsOnStart;

    public synchronized BorderParams getBorderParamsOnStart() {
        return borderParamsOnStart;
    }

    public synchronized void setBorderParamsOnStart(BorderParams borderParamsOnStart) {
        this.borderParamsOnStart = borderParamsOnStart;
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
                ? firstOpenPrice.subtract(secondOpenPrice).setScale(2, BigDecimal.ROUND_HALF_UP)
                : BigDecimal.ZERO;
    }

    public synchronized BigDecimal getDelta2Fact() {
        final BigDecimal firstOpenPrice = bPriceFact.getAvg();
        final BigDecimal secondOpenPrice = oPriceFact.getAvg();
        return (firstOpenPrice != null && secondOpenPrice != null)
                ? secondOpenPrice.subtract(firstOpenPrice).setScale(2, BigDecimal.ROUND_HALF_UP)
                : BigDecimal.ZERO;
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

    public synchronized void setBorder(BigDecimal border) {
        this.borderList = Collections.singletonList(border);
    }

    public synchronized List<BigDecimal> getBorderList() {
        return borderList;
    }

    public synchronized void setBorderList(List<BigDecimal> borderList) {
        this.borderList = Collections.unmodifiableList(borderList);
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

    public static class Details {
        final public BigDecimal val;
        final public String str;

        public Details(BigDecimal val, String str) {
            this.val = val;
            this.str = str;
        }
    }
}
