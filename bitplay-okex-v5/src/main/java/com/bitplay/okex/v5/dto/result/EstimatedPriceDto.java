package com.bitplay.okex.v5.dto.result;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import lombok.Data;

@Data
public class EstimatedPriceDto {

    private List<EstimatedPrice> data;

    @Data
    public static class EstimatedPrice {

        /**
         * Instrument type FUTURES OPTION
         */
        private String instType;
        /**
         * Instrument ID, e.g. BTC-USD-180216
         */
        private String instId;
        /**
         * Estimated delivery price
         */
        private BigDecimal settlePx;
        /**
         * time
         */
        private Date ts;
    }


}
