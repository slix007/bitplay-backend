package com.bitplay.okex.v5.dto.result;

import java.util.List;
import lombok.Data;

@Data
public class OpenOrdersResult {

    Boolean result; // true
    List<OrderDetail> order_info;

}
