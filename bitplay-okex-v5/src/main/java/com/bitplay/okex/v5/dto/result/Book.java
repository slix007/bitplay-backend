package com.bitplay.okex.v5.dto.result;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import lombok.Data;

@Data
public class Book {

    @Data
    public static class BookData {
        private BigDecimal[][] asks;
        private BigDecimal[][] bids;
        private Date ts;
    }

    private String code;
    private String msg;
    private List<BookData> data;


}
