package com.bitplay.api.domain;

import com.fasterxml.jackson.annotation.JsonFormat;

import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.util.Date;

/**
 * Created by Sergey Shurmin on 2/25/18.
 */
public class Delta {

    @JsonFormat(pattern="HH:mm:ss.SSS")
    private Date timestamp;
    private BigDecimal bAsk;
    private BigDecimal bBid;
    private BigDecimal oAsk;
    private BigDecimal oBid;
    private BigDecimal bDelta;
    private BigDecimal oDelta;

    public Delta() {
    }

    public Delta(Date timestamp, BigDecimal bDelta, BigDecimal oDelta) {
        this.timestamp = timestamp;
        this.bDelta = bDelta;
        this.oDelta = oDelta;
    }

    public Delta(Date timestamp, BigDecimal bAsk, BigDecimal bBid, BigDecimal oAsk, BigDecimal oBid) {
        this.timestamp = timestamp;
        this.bAsk = bAsk;
        this.bBid = bBid;
        this.oAsk = oAsk;
        this.oBid = oBid;
        this.bDelta = bBid.subtract(oAsk);
        this.oDelta = oBid.subtract(bAsk);
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public BigDecimal getbAsk() {
        return bAsk;
    }

    public void setbAsk(BigDecimal bAsk) {
        this.bAsk = bAsk;
    }

    public BigDecimal getbBid() {
        return bBid;
    }

    public void setbBid(BigDecimal bBid) {
        this.bBid = bBid;
    }

    public BigDecimal getoAsk() {
        return oAsk;
    }

    public void setoAsk(BigDecimal oAsk) {
        this.oAsk = oAsk;
    }

    public BigDecimal getoBid() {
        return oBid;
    }

    public void setoBid(BigDecimal oBid) {
        this.oBid = oBid;
    }

    public BigDecimal getbDelta() {
        return bDelta;
    }

    public void setbDelta(BigDecimal bDelta) {
        this.bDelta = bDelta;
    }

    public BigDecimal getoDelta() {
        return oDelta;
    }

    public void setoDelta(BigDecimal oDelta) {
        this.oDelta = oDelta;
    }
}
