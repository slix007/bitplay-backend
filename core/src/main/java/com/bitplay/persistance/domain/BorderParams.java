package com.bitplay.persistance.domain;

import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Created by Sergey Shurmin on 7/18/17.
 */
@Document(collection = "bordersCollection")
@TypeAlias("borders")
public class BorderParams extends AbstractDocument {

    private Ver activeVersion = Ver.V1;
    private BordersV1 bordersV1;
    private BordersV2 bordersV2;
    public BorderParams(BordersV1 bordersV1, BordersV2 bordersV2) {
        this.setId(1L);
        this.bordersV1 = bordersV1;
        this.bordersV2 = bordersV2;
    }

    public Ver getActiveVersion() {
        return activeVersion;
    }

    public void setActiveVersion(Ver activeVersion) {
        this.activeVersion = activeVersion;
    }

    public BordersV1 getBordersV1() {
        return bordersV1;
    }

    public void setBordersV1(BordersV1 bordersV1) {
        this.bordersV1 = bordersV1;
    }

    public BordersV2 getBordersV2() {
        return bordersV2;
    }

    public void setBordersV2(BordersV2 bordersV2) {
        this.bordersV2 = bordersV2;
    }

    public enum Ver {V1, V2,}
}
