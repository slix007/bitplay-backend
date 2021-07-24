package com.bitplay.okex.v5.dto.result;

import java.math.BigDecimal;
import java.util.Date;
import lombok.Data;

@Data
public class Book {

    private BigDecimal[][] asks;
    private BigDecimal[][] bids;
    private Date timestamp;

}
