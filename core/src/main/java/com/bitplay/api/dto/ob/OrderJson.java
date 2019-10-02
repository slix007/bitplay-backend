package com.bitplay.api.dto.ob;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Created by Sergey Shurmin on 4/22/17.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderJson {

    String arbId;
    String id;
    String status;
    String currency;
    String price;
    String amount;
    String orderType;
    String timestamp;
    String amountInBtc;
    String filledAmount;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        OrderJson orderJson = (OrderJson) o;

        if (currency != null ? !currency.equals(orderJson.currency) : orderJson.currency != null) return false;
        if (price != null ? !price.equals(orderJson.price) : orderJson.price != null) return false;
        if (amount != null ? !amount.equals(orderJson.amount) : orderJson.amount != null) return false;
        return orderType != null ? orderType.equals(orderJson.orderType) : orderJson.orderType == null;
    }

    @Override
    public int hashCode() {
        int result = currency != null ? currency.hashCode() : 0;
        result = 31 * result + (price != null ? price.hashCode() : 0);
        result = 31 * result + (amount != null ? amount.hashCode() : 0);
        result = 31 * result + (orderType != null ? orderType.hashCode() : 0);
        return result;
    }
}
