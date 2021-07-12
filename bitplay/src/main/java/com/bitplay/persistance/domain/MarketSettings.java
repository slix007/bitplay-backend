package com.bitplay.persistance.domain;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "marketsCollection")
@TypeAlias("markets")
@Data
public class MarketSettings {

    @Id
    private Integer marketId;

    private String marketName;
}
