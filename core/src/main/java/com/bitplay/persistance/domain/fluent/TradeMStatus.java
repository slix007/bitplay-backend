package com.bitplay.persistance.domain.fluent;

/**
 * Created by Sergey Shurmin on 2/6/18.
 */
public enum TradeMStatus {
    WAITING,
    IN_PROGRESS,
    FINISHED,
    NONE, // when plan amount is 0
    COMPLETED, // not it use
}
