package com.bitplay.persistance.domain;

import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Created by Sergey Shurmin on 10/31/17.
 */
@Document(collection = "TimeCompareRangeCollection")
@TypeAlias("timeCompareRange")
public class TimeCompareRange extends AbstractDocument {

    private Range bitmexOurReq;
    private Range ourRespOurReq;
    private Range ourRespBitmex;

    public Range getBitmexOurReq() {
        return bitmexOurReq;
    }

    public void setBitmexOurReq(Range bitmexOurReq) {
        this.bitmexOurReq = bitmexOurReq;
    }

    public Range getOurRespOurReq() {
        return ourRespOurReq;
    }

    public void setOurRespOurReq(Range ourRespOurReq) {
        this.ourRespOurReq = ourRespOurReq;
    }

    public Range getOurRespBitmex() {
        return ourRespBitmex;
    }

    public void setOurRespBitmex(Range ourRespBitmex) {
        this.ourRespBitmex = ourRespBitmex;
    }
}
