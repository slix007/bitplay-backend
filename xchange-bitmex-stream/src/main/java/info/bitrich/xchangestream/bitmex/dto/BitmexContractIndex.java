package info.bitrich.xchangestream.bitmex.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Date;
import org.knowm.xchange.dto.marketdata.ContractIndex;

/**
 * Created by Sergey Shurmin on 8/6/17.
 */
public class BitmexContractIndex extends ContractIndex {

    private BigDecimal markPrice;
    private BigDecimal lastPrice;
    private BigDecimal fundingRate;
    private OffsetDateTime swapTime;

    public BitmexContractIndex(BigDecimal indexPrice, Date timestamp) {
        super(indexPrice, timestamp);
    }

    public BitmexContractIndex(BigDecimal indexPrice, BigDecimal markPrice, BigDecimal lastPrice, Date timestamp, BigDecimal fundingRate,
            OffsetDateTime swapTime) {
        super(indexPrice, timestamp);
        this.markPrice = markPrice;
        this.lastPrice = lastPrice;
        this.fundingRate = fundingRate;
        this.swapTime = swapTime;
    }

    public BigDecimal getMarkPrice() {
        return markPrice;
    }

    public BigDecimal getLastPrice() {
        return lastPrice;
    }

    public BigDecimal getFundingRate() {
        return fundingRate;
    }

    public OffsetDateTime getSwapTime() {
        return swapTime;
    }
}
