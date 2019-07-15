package com.bitplay.arbitrage.dto;

import com.bitplay.persistance.domain.fluent.DeltaName;
import com.bitplay.persistance.domain.settings.TradingMode;
import java.io.Serializable;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.ToString;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.springframework.data.annotation.Transient;

/**
 * Created by Sergey Shurmin on 4/27/17.
 */
@Getter
@ToString
public class BestQuotes implements Serializable {

    private final BigDecimal ask1_o;
    private final BigDecimal ask1_p;
    private final BigDecimal bid1_o;
    private final BigDecimal bid1_p;

    // pre-signal params
    @Transient
    private boolean preSignalReChecked = false;
    @Transient
    private boolean needPreSignalReCheck = false;
    @Transient
    private DeltaName deltaName;
    @Transient
    private TradingMode tradingMode;

    // Bitmex OB for first attempt after signal.
    // no copy. Set null after using.
    @Transient
    private volatile OrderBook btmOrderBook;

    public BestQuotes(BigDecimal ask1_o, BigDecimal ask1_p, BigDecimal bid1_o, BigDecimal bid1_p) {
        this.ask1_o = ask1_o;
        this.ask1_p = ask1_p;
        this.bid1_o = bid1_o;
        this.bid1_p = bid1_p;
    }

    public static BestQuotes empty() {
        return new BestQuotes(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public boolean hasEmpty() {
        return ask1_o == null || ask1_o.signum() == 0
                && ask1_p == null || ask1_p.signum() == 0
                && bid1_o == null || bid1_o.signum() == 0
                && bid1_p == null || bid1_p.signum() == 0;
    }

    public void setPreSignalReChecked(DeltaName deltaName, TradingMode tradingMode) {
        this.preSignalReChecked = true;
        this.needPreSignalReCheck = false;
        this.deltaName = deltaName;
        this.tradingMode = tradingMode;
    }

    public void setNeedPreSignalReCheck() {
        this.needPreSignalReCheck = true;
        this.preSignalReChecked = false;
    }

    public void setBtmOrderBook(OrderBook btmOrderBook) {
        this.btmOrderBook = btmOrderBook;
    }

    public String toStringEx() {
        // b_delta (xx) = bid[1] (xx) - ask[1] (xx), o_delta (xx) = bid[1] (xx) - ask[1] (xx);
        return String.format("b_delta (%s) = bid[1] (%s) - ask[1] (%s), o_delta (%s) = bid[1] (%s) - ask[1] (%s);",
                bid1_p.subtract(ask1_o),
                bid1_p, ask1_o,
                bid1_o.subtract(ask1_p),
                bid1_o, ask1_p
        );
    }

    public BigDecimal getBDelta() {
        return bid1_p.subtract(ask1_o);
    }

    public BigDecimal getODelta() {
        return bid1_o.subtract(ask1_p);
    }
}
