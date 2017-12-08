package com.bitplay.persistance.domain.settings;

/**
 * Created by Sergey Shurmin on 12/3/17.
 */
public class SysOverloadArgs {

    private Integer placeAttempts;
    private Integer movingErrorsForOverload;
    private Integer overloadTimeSec;

    public static SysOverloadArgs defaults() {
        final SysOverloadArgs newObj = new SysOverloadArgs();
        newObj.placeAttempts = 3;
        newObj.movingErrorsForOverload = 3;
        newObj.overloadTimeSec = 60;
        return newObj;
    }

    public Integer getPlaceAttempts() {
        return placeAttempts;
    }

    public void setPlaceAttempts(Integer placeAttempts) {
        this.placeAttempts = placeAttempts;
    }

    public Integer getMovingErrorsForOverload() {
        return movingErrorsForOverload;
    }

    public void setMovingErrorsForOverload(Integer movingErrorsForOverload) {
        this.movingErrorsForOverload = movingErrorsForOverload;
    }

    public Integer getOverloadTimeSec() {
        return overloadTimeSec;
    }

    public void setOverloadTimeSec(Integer overloadTimeSec) {
        this.overloadTimeSec = overloadTimeSec;
    }

    @Override
    public String toString() {
        return "SysOverloadArgs{" +
                "placeAttempts=" + placeAttempts +
                ", movingErrorsForOverload=" + movingErrorsForOverload +
                ", overloadTimeSec=" + overloadTimeSec +
                '}';
    }
}
