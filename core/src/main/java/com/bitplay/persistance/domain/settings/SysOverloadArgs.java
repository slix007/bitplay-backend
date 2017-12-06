package com.bitplay.persistance.domain.settings;

/**
 * Created by Sergey Shurmin on 12/3/17.
 */
public class SysOverloadArgs {

    private Integer errorsCountForOverload;
    private Integer overloadTimeSec;
    private Integer movingErrorsResetTimeout;


    public static SysOverloadArgs defaults() {
        final SysOverloadArgs newObj = new SysOverloadArgs();
        newObj.errorsCountForOverload = 3;
        newObj.overloadTimeSec = 60;
        newObj.movingErrorsResetTimeout = 60;
        return newObj;
    }

    public Integer getErrorsCountForOverload() {
        return errorsCountForOverload;
    }

    public void setErrorsCountForOverload(Integer errorsCountForOverload) {
        this.errorsCountForOverload = errorsCountForOverload;
    }

    public Integer getOverloadTimeSec() {
        return overloadTimeSec;
    }

    public void setOverloadTimeSec(Integer overloadTimeSec) {
        this.overloadTimeSec = overloadTimeSec;
    }

    public Integer getMovingErrorsResetTimeout() {
        return movingErrorsResetTimeout;
    }

    public void setMovingErrorsResetTimeout(Integer movingErrorsResetTimeout) {
        this.movingErrorsResetTimeout = movingErrorsResetTimeout;
    }

    @Override
    public String toString() {
        return "SysOverloadArgs{" +
                "errorsCountForOverload=" + errorsCountForOverload +
                ", overloadTimeSec=" + overloadTimeSec +
                ", movingErrorsResetTimeout=" + movingErrorsResetTimeout +
                '}';
    }
}
