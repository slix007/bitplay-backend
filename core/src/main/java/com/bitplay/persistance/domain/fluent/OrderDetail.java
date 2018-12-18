package com.bitplay.persistance.domain.fluent;

import java.math.BigDecimal;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.knowm.xchange.dto.Order;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * Created by Sergey Shurmin on 12/23/17.
 */
@Data
@NoArgsConstructor
public class OrderDetail {
    private Order.OrderStatus orderStatus;
    private Order.OrderType orderType;
    private BigDecimal tradableAmount;
    private BigDecimal cumulativeAmount;
    private BigDecimal averagePrice;
    private CurrencyPairDetail currencyPair;
    private String id;
    @Field
    @Indexed(expireAfterSeconds = 3600 * 24 * 31) // one month
    private Date timestamp;
    private BigDecimal limitPrice;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CurrencyPairDetail {

        private String first;
        private String second;
    }

    public String getContractType() {
        return currencyPair != null ? currencyPair.toString() : "";
    }
}
