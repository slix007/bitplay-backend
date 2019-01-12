package com.bitplay.api.domain.ob;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Created by Sergey Shurmin on 4/7/18.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class InsideLimitsEx {

    private Boolean main;
    private Boolean btmDelta;
    private Boolean okDelta;
    private Boolean adjBuy;
    private Boolean adjSell;
    private Boolean corrBuy;
    private Boolean corrSell;
}
