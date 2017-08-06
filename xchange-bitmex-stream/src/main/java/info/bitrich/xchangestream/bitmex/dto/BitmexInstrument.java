package info.bitrich.xchangestream.bitmex.dto;

import org.knowm.xchange.dto.marketdata.ContractIndex;

import java.math.BigDecimal;
import java.util.Date;

/**
 * Created by Sergey Shurmin on 8/6/17.
 */
public class BitmexInstrument extends ContractIndex {

    private BigDecimal fundingRate;
    private Date fundingTimestamp;

    public BitmexInstrument(BigDecimal indexPrice, Date timestamp) {
        super(indexPrice, timestamp);
    }

    public BitmexInstrument(BigDecimal indexPrice, Date timestamp, BigDecimal fundingRate, Date fundingTimestamp) {
        super(indexPrice, timestamp);
        this.fundingRate = fundingRate;
        this.fundingTimestamp = fundingTimestamp;
    }

    public BigDecimal getFundingRate() {
        return fundingRate;
    }

    public Date getFundingTimestamp() {
        return fundingTimestamp;
    }
}
