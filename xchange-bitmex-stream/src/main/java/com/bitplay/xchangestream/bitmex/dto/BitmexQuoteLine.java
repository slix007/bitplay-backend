package com.bitplay.xchangestream.bitmex.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * Created by Sergey Shurmin on 9/26/17.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class BitmexQuoteLine {

    private final String action; //partial, insert
    private final List<BitmexQuote> data;

}
