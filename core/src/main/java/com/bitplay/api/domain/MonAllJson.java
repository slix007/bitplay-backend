package com.bitplay.api.domain;

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
    private Mon monBitmexPlacing;
    private Mon monBitmexMoving;
    private Mon monOkexPlacing;
    private Mon monOkexMoving;

}
