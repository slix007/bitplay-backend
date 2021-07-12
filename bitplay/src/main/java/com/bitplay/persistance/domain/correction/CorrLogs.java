package com.bitplay.persistance.domain.correction;

import com.bitplay.persistance.domain.AbstractDocument;

import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Created by Sergey Shurmin on 3/21/18.
 */
@Document(collection = "corrLogs")
@TypeAlias("corrLogs")
public class CorrLogs extends AbstractDocument {
    private String signalType;
    private CorrType corrType;

    public CorrLogs() {
    }

    public CorrLogs(String signalType, CorrType corrType) {
        this.signalType = signalType;
        this.corrType = corrType;
    }

    public String getSignalType() {
        return signalType;
    }

    public void setSignalType(String signalType) {
        this.signalType = signalType;
    }

    public CorrType getCorrType() {
        return corrType;
    }

    public void setCorrType(CorrType corrType) {
        this.corrType = corrType;
    }
}
