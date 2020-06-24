package com.bitplay.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Created by Sergey Shurmin on 4/24/17.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SumBalJson {
    private String result;
    private String sumBalImpliedString;
    private String s_e_best;
    private String s_e_best_min;
    private String s_e_best_min_time_to_forbidden;
    private String cold_storage_btc;
    private String cold_storage_eth;

}
