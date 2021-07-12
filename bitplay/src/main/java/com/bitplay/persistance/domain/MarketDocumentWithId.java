package com.bitplay.persistance.domain;

import lombok.Data;
import org.springframework.data.mongodb.core.index.Indexed;

/**
 * Created by Sergey Shurmin on 7/18/17.
 */
@Data
public class MarketDocumentWithId extends AbstractDocument {

    @Indexed
    private String marketName;

    private String typeName;
}
