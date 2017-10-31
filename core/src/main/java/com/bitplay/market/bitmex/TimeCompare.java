package com.bitplay.market.bitmex;

import com.bitplay.persistance.domain.TimeCompareRange;

/**
 * Created by Sergey Shurmin on 10/31/17.
 */
public class TimeCompare {

    private String timeCompareString;
    private TimeCompareRange timeCompareRange;

    public String getTimeCompareString() {
        return timeCompareString;
    }

    public void setTimeCompareString(String timeCompareString) {
        this.timeCompareString = timeCompareString;
    }

    public TimeCompareRange getTimeCompareRange() {
        return timeCompareRange;
    }

    public void setTimeCompareRange(TimeCompareRange timeCompareRange) {
        this.timeCompareRange = timeCompareRange;
    }
}
