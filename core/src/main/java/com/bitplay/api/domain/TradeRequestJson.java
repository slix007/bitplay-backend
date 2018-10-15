package com.bitplay.api.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Created by Sergey Shurmin on 4/15/17.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@NoArgsConstructor
public class TradeRequestJson {

    public enum Type {
        BUY,
        SELL,
    }
    public enum PlacementType {
        TAKER,
        MAKER,
        HYBRID,
        MAKER_TICK,
        HYBRID_TICK,
    }

    private Type type;
    private PlacementType placementType;
    private String amount;
    private String toolName;

}
