package com.bitplay.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

/**
 * Created by Sergey Shurmin on 6/1/17.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class MarketFlagsJson {
    Boolean firstMarket;
    Boolean secondMarket;

    public MarketFlagsJson(Boolean firstMarket, Boolean secondMarket) {
        this.firstMarket = firstMarket;
        this.secondMarket = secondMarket;
    }
}
