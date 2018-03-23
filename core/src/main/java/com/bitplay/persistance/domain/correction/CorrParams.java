package com.bitplay.persistance.domain.correction;

import com.bitplay.persistance.domain.ExchangePair;
import com.bitplay.persistance.domain.ExchangePairDocument;

import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Created by Sergey Shurmin on 7/18/17.
 */
@Document(collection = "corrParamsCollection")
@TypeAlias("corrParams")
public class CorrParams extends ExchangePairDocument {

    private Integer corrCount1;
    private Integer corrCount2;
    private Integer failedCount;
    private CorrError corrError;

    public CorrParams() {
    }

    public CorrParams(Integer corrCount1, Integer corrCount2, Integer failedCount, CorrError corrError) {
        this.corrCount1 = corrCount1;
        this.corrCount2 = corrCount2;
        this.failedCount = failedCount;
        this.corrError = corrError;
    }

    public static CorrParams createDefault() {
        final CorrParams corrParams = new CorrParams(0, 0, 0, CorrError.createDefault());
        corrParams.setId(1L);
        corrParams.setExchangePair(ExchangePair.BITMEX_OKEX);
        return corrParams;
    }

    public Integer getCorrCount1() {
        return corrCount1;
    }

    public void setCorrCount1(Integer corrCount1) {
        this.corrCount1 = corrCount1;
    }

    public Integer getCorrCount2() {
        return corrCount2;
    }

    public void setCorrCount2(Integer corrCount2) {
        this.corrCount2 = corrCount2;
    }

    public CorrError getCorrError() {
        return corrError;
    }

    public Integer getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(Integer failedCount) {
        this.failedCount = failedCount;
    }

    public void setCorrError(CorrError corrError) {
        this.corrError = corrError;
    }

    public void incCorrCounter1() {
        this.corrCount1++;
    }

    public void incCorrCounter2() {
        this.corrCount2++;
    }

    public void incFailedCount() {
        if (failedCount == null) {
            failedCount = 0;
        }
        this.failedCount++;
    }
}
