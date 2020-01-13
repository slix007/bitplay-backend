package com.bitplay.market.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.Duration;
import java.time.Instant;

/**
 * Created by Sergey Shurmin on 11/19/17.
 */
@Setter
@Getter
@ToString
public class PlBefore {

    private Instant createQuote;
    private Instant getQuote;
    private Instant saveQuote;
    private Instant signalCheck;
    private Instant signalTime;
    private Instant requestPlacing;
    private Instant getAnswerFromPlacing;

//    private Instant lastObTime;
//    private Instant startSignalCheck;
//    private Instant startSignalTime;
//    private Instant addPlacingTask;
//    private Instant startPlacingTask;
//    private Instant startPlacing;

    public PlBefore() {
    }

    public PlBefore(Instant lastObTime, Instant startSignalCheck) {
        this.lastObTime = lastObTime;
        this.startSignalCheck = startSignalCheck;
    }

    public long calcPlBeforeToCheckTime() {
        return Duration.between(lastObTime, startSignalCheck).toMillis();
    }

    public long calcPlBeforeCheckTime() {
        return Duration.between(startSignalCheck, startSignalTime).toMillis();
    }

    public long calcPlBeforeAddPlacingTask() {
        return Duration.between(startSignalTime, addPlacingTask).toMillis();
    }

    public long calcPlBeforeStartPlacingTask() {
        return Duration.between(addPlacingTask, startPlacingTask).toMillis();
    }

    public long calcPlBeforeStartPlacingTaskToStart() {
        return Duration.between(startPlacingTask, startPlacing).toMillis();
    }

    public long calcPlBeforePreparePlacingTime() {
        return Duration.between(startSignalTime, startPlacing).toMillis();
    }


}
