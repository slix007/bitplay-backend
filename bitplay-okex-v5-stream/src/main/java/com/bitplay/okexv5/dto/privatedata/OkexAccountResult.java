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
    private BigDecimal isoEq;// Isolated margin equity of the currency
    private BigDecimal liab;// Liabilities of the currency


//    private BigDecimal fixed_balance;
//    private BigDecimal maint_margin_ratio;
//    private BigDecimal margin;
//    private BigDecimal margin_frozen;
//    private String margin_mode;
//    private BigDecimal realized_pnl;
//    private LocalDateTime timestamp;
//    private BigDecimal total_avail_balance;

}
