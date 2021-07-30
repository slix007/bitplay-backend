package com.bitplay.okex.v5.dto.result;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import lombok.Data;

@Data
public class SwapFundingTime {

    @Data
    public static class SwapFundingTimeData {
        private String instType;
        private String instId;
        private Date fundingTime;
        private Date nextFundingTime;
        private BigDecimal fundingRate;
        private BigDecimal nextFundingRate;
    }

    private String code;
    private String msg;
    private List<SwapFundingTimeData> data;

    public SwapFundingTimeData getData() {
        if (data != null && !data.isEmpty()) {
            return data.get(0);
        }
        return null;
    }
}
