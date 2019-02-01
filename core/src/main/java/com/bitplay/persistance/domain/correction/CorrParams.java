package com.bitplay.persistance.domain.correction;

import com.bitplay.persistance.domain.ExchangePair;
import com.bitplay.persistance.domain.ExchangePairDocument;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Created by Sergey Shurmin on 7/18/17.
 */
@Document(collection = "corrParamsCollection")
@TypeAlias("corrParams")
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class CorrParams extends ExchangePairDocument {

    private Corr corr;
    private Preliq preliq;
    private Adj adj;

    public static CorrParams createDefault() {
        final CorrParams corrParams = new CorrParams();
        corrParams.corr = Corr.createDefault();
        corrParams.preliq = Preliq.createDefault();
        corrParams.adj = Adj.createDefault();

        corrParams.setId(1L);
        corrParams.setExchangePair(ExchangePair.BITMEX_OKEX);
        return corrParams;
    }

    public void setSettingsParts(CorrParams corrParams) {
        this.corr.setSettingsParts(corrParams.corr);
        this.preliq.setSettingsParts(corrParams.preliq);
        this.adj.setSettingsParts(corrParams.adj);
    }
}
