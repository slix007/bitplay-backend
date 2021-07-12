package com.bitplay.xchange.dto.marketdata;

import java.math.BigDecimal;
import java.util.Date;

/**
 * Created by Sergey Shurmin on 6/26/17.
 */
public class ContractIndex {
    private BigDecimal indexPrice;
    private Date timestamp;

    public ContractIndex(BigDecimal indexPrice, Date timestamp) {
        this.indexPrice = indexPrice;
        this.timestamp = timestamp;
    }

    public BigDecimal getIndexPrice() {
        return indexPrice;
    }

    public Date getTimestamp() {
        return timestamp;
    }
}
