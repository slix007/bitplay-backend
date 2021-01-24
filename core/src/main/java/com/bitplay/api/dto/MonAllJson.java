package com.bitplay.api.dto;

import com.bitplay.persistance.domain.mon.Mon;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Created by Sergey Shurmin on 4/24/17.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class MonAllJson {

    private String result;
    private String allHtml;
    private String bitmexReconnectCount;
    private Mon monBitmexPlacing;
    private Mon monBitmexMoving;
    private Mon monOkexPlacing;
    private Mon monOkexMoving;
    private String xrateLimitBtm;
    private String xrateLimitBtmUpdated;
    private String xrateLimitBtmResetAt;
    private String xrateLimitBtm1s;
    private String xrateLimitBtmUpdated1s;
    private String xrateLimitBtmResetAt1s;

}
