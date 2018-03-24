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


    private Corr corr;
    private Preliq preliq;

    public CorrParams() {
    }

    public CorrParams(Corr corr, Preliq preliq) {
        this.corr = corr;
        this.preliq = preliq;
    }

    public static CorrParams createDefault() {
        final CorrParams corrParams = new CorrParams(Corr.createDefault(), Preliq.createDefault());
        corrParams.setId(1L);
        corrParams.setExchangePair(ExchangePair.BITMEX_OKEX);
        return corrParams;
    }

    public Corr getCorr() {
        return corr;
    }

    public void setCorr(Corr corr) {
        this.corr = corr;
    }

    public Preliq getPreliq() {
        return preliq;
    }

    public void setPreliq(Preliq preliq) {
        this.preliq = preliq;
    }
}
