package com.bitplay.persistance.domain.fluent;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Created by Sergey Shurmin on 2/6/18.
 */
@Getter
@AllArgsConstructor
public enum DeltaName {
    B_DELTA("1"),
    O_DELTA("2"),
    ;

    private String deltaNumber;

    public String getNameWithNumber() {
        return "delta" + deltaNumber;
    }
}
