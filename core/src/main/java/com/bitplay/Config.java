package com.bitplay;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Created by Sergey Shurmin on 4/29/17.
 */
@Component
@Getter
public class Config {

    @Value("${market.first}")
    private String firstMarketName;
    @Value("${market.first.key}")
    private String firstMarketKey;
    @Value("${market.first.secret}")
    private String firstMarketSecret;

    @Value("${market.second}")
    private String secondMarketName;
    @Value("${market.second.key}")
    private String secondMarketKey;
    @Value("${market.second.secret}")
    private String secondMarketSecret;

    @Value("${deltas-series.enabled}")
    private Boolean deltasSeriesEnabled;

    @Value("${e_best_min}")
    private Integer eBestMin;

}
