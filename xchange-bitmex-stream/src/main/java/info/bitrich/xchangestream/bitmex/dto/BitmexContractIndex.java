package info.bitrich.xchangestream.bitmex.dto;

import org.knowm.xchange.dto.marketdata.ContractIndex;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Date;

/**
 * Created by Sergey Shurmin on 8/6/17.
 */
public class BitmexContractIndex extends ContractIndex {

    private BigDecimal fundingRate;
    private OffsetDateTime fundingTimestamp;

    public BitmexContractIndex(BigDecimal indexPrice, Date timestamp) {
        super(indexPrice, timestamp);
    }

    public BitmexContractIndex(BigDecimal indexPrice, Date timestamp, BigDecimal fundingRate, OffsetDateTime fundingTimestamp) {
        super(indexPrice, timestamp);
        this.fundingRate = fundingRate;
        this.fundingTimestamp = fundingTimestamp;
    }

    public BigDecimal getFundingRate() {
        return fundingRate;
    }

    public OffsetDateTime getFundingTimestamp() {
        return fundingTimestamp;
    }
}
