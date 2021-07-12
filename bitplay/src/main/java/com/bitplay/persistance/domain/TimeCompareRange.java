package com.bitplay.persistance.domain;

import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Created by Sergey Shurmin on 10/31/17.
 */
@Document(collection = "TimeCompareRangeCollection")
@TypeAlias("timeCompareRange")
public class TimeCompareRange extends AbstractDocument {

    public static TimeCompareRange empty() {
        TimeCompareRange one = new TimeCompareRange();
        one.setId(1L);
        one.setBitmexOurReq(Range.empty());
        one.setOurRespOurReq(Range.empty());
        one.setOurRespBitmex(Range.empty());
        return one;
    }

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
