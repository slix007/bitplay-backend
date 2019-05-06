package com.bitplay.persistance.domain.settings;

import lombok.Data;

/**
 * Created by Sergey Shurmin on 12/3/17.
 */
@Data
public class SysOverloadArgs {

    private Integer placeAttempts;
    private Integer movingErrorsForOverload;
    private Integer overloadTimeSec;
    private Integer betweenAttemptsMs;

    public static SysOverloadArgs defaults() {
        final SysOverloadArgs newObj = new SysOverloadArgs();
        newObj.placeAttempts = 3;
        newObj.movingErrorsForOverload = 3;
        newObj.overloadTimeSec = 60;
        newObj.betweenAttemptsMs = 500;
        return newObj;
    }

    public int getBetweenAttemptsMsSafe() {
        return betweenAttemptsMs != null ? betweenAttemptsMs : 0;
    }
}
