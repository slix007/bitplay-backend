package com.bitplay.okexv5.dto.privatedata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Created by Sergey Shurmin on 6/10/17.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class OkexAccountResult {

    private BigDecimal availEq;
    private BigDecimal mgnRatio;
    private BigDecimal upl;
    private String ccy;
    private BigDecimal eq;
    private BigDecimal frozenBal; // Margin frozen for open
    private BigDecimal liab;// Liabilities of the currency


//    private BigDecimal availBal; // Margin frozen for open orders
//    private BigDecimal isoEq;// Isolated margin equity of the currency
//    private BigDecimal ordFrozen; // Margin frozen for open orders
//    private BigDecimal cashBal; // Margin frozen for open orders
//    private BigDecimal crossLiab; // Margin frozen for open orders
//    private BigDecimal disEq; // Margin frozen for open orders
//
//    private BigDecimal eqUsd; // Margin frozen for open
//    private BigDecimal interest; // Margin frozen for open
//    private BigDecimal isoLiab; // Margin frozen for open
//    private BigDecimal isoUpl; // Margin frozen for open

//            "liab": "0",
//            "maxLoan": "1453.92289531493594",
//            "mgnRatio": "",
//            "notionalLever": "",
//            "ordFrozen": "0",
//            "twap": "0",
//            "uTime": "1620722938250",
//            "upl": "0.570822125136023",
//            "uplLiab": "0",
//            "stgyEq":"0"


//    private BigDecimal fixed_balance;
//    private BigDecimal maint_margin_ratio;
//    private BigDecimal margin;
//    private BigDecimal margin_frozen;
//    private String margin_mode;
//    private BigDecimal realized_pnl;
//    private LocalDateTime timestamp;
//    private BigDecimal total_avail_balance;

}
