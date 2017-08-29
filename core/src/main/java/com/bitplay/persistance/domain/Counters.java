package com.bitplay.persistance.domain;

import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Created by Sergey Shurmin on 7/18/17.
 */
@Document(collection = "countersCollection")
@TypeAlias("counters")
public class Counters extends MarketDocument {
    private int corrCounter1 = 0;
    private int corrCounter2 = 0;

    public int getCorrCounter1() {
        return corrCounter1;
    }

    public void setCorrCounter1(int corrCounter1) {
        this.corrCounter1 = corrCounter1;
    }

    public int getCorrCounter2() {
        return corrCounter2;
    }

    public void setCorrCounter2(int corrCounter2) {
        this.corrCounter2 = corrCounter2;
    }

    public void incCorrCounter1() {
        corrCounter1 = corrCounter1 + 1;
    }

    public void incCorrCounter2() {
        corrCounter2 = corrCounter2 + 1;
    }
}
