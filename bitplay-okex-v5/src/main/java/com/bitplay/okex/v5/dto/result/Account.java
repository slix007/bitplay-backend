package com.bitplay.okex.v5.dto.result;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class Account {

    private String code;
    private String msg;
    private List<AccountData> data;

    @Data
    public static class AccountData {
        private BigDecimal totalEq;     // Total equity in USD level
        private BigDecimal imr;         // Initial margin requirement in USD level
        private BigDecimal mmr;         // Maintenance margin requirement in USD level
        private BigDecimal mgnRatio;    // Margin ratio in USD level
        private BigDecimal notionalUsd; // Quantity of positions usd
        private BigDecimal isoEq;       // Isolated margin equity in USD level
        private BigDecimal adjEq;       // Adjusted/Effective equity in USD level
        private BigDecimal ordFroz;     // Margin frozen for pending orders in USD level

        private LocalDateTime uTime;
        private List<DetailsData> details;

        public DetailsData getDetails() {
            return details != null && !details.isEmpty()
                    ? details.get(0) : null;
        }

        @Data
        public static class DetailsData {

            private String ccy;
            private BigDecimal eq;
            private BigDecimal isoEq;
            private BigDecimal availEq;
            private BigDecimal frozenBal;
            private BigDecimal upl;
            private BigDecimal mgnRatio;
            private BigDecimal liab;
        }
    }

}
