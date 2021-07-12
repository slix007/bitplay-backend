package com.bitplay.persistance.domain.settings;

import lombok.Data;

/**
 * Created by Sergey Shurmin on 10/5/19.
 */
@Data
public class OkexPostOnlyArgs {

    private Boolean postOnlyEnabled;
    private Boolean postOnlyWithoutLast;
    private Integer postOnlyAttempts;
    private Integer postOnlyBetweenAttemptsMs;

    public static OkexPostOnlyArgs defaults() {
        final OkexPostOnlyArgs newObj = new OkexPostOnlyArgs();
        newObj.postOnlyEnabled = false;
        newObj.postOnlyWithoutLast = false;
        newObj.postOnlyAttempts = 5;
        newObj.postOnlyBetweenAttemptsMs = 50;
        return newObj;
    }


}
