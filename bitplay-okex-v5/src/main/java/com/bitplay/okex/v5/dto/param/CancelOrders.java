package com.bitplay.okex.v5.dto.param;

import java.util.List;

/**
 * Close Position
 *
 * @author wc.j
 * @version 1.0.0
 * @date 2018/10/19 16:54
 */
public class CancelOrders {

    public List<Long> getOrder_ids() {
        return order_ids;
    }

    public void setOrder_ids(List<Long> order_ids) {
        this.order_ids = order_ids;
    }

    List<Long> order_ids;
}
