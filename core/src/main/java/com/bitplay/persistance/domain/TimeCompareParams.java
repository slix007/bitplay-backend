package com.bitplay.persistance.domain;

import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Created by Sergey Shurmin on 11/1/17.
 */
@Document(collection = "TimeCompareParamsCollection")
@TypeAlias("timeCompareParams")
public class TimeCompareParams extends AbstractDocument {
    Integer updateSeconds;

    public Integer getUpdateSeconds() {
        return updateSeconds;
    }

    public void setUpdateSeconds(Integer updateSeconds) {
        this.updateSeconds = updateSeconds;
    }
}
