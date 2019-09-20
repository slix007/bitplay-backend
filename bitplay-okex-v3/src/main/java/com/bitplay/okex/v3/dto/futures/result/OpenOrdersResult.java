package com.bitplay.okex.v3.dto.futures.result;

import java.util.List;
import lombok.Data;

@Data
public class OpenOrdersResult {

    Boolean result; // true
    List<OrderDetail> order_info;

}
