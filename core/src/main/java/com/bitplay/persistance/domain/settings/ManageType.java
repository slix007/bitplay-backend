package com.bitplay.persistance.domain.settings;

public enum ManageType {
    AUTO,
    MANUAL,
    ;

    public boolean isAuto() {
        return this == AUTO;
    }

//    public boolean isManual() {
//        return this == MANUAL;
//    }
}
