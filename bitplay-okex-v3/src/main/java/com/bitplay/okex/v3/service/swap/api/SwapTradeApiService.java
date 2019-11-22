package com.bitplay.okex.v3.service.swap.api;

//import com.okcoin.commons.okex.open.api.bean.swap.param.PpCancelOrderVO;
//import com.okcoin.commons.okex.open.api.bean.swap.param.PpOrder;
//import com.okcoin.commons.okex.open.api.bean.swap.param.PpOrders;

import com.bitplay.externalapi.PrivateApi;
import com.bitplay.okex.v3.dto.futures.result.OkexAllPositions;
import com.bitplay.okex.v3.dto.futures.result.OkexOnePosition;

public interface SwapTradeApiService extends PrivateApi {


    /**
     * Get all of swap contract position list
     */
    OkexAllPositions getPositions();
    Object testPositions();

    /**
     * Get the swap contract product position
     *
     * @param instrumentId The id of the futures contract eg: BTC-USD-0331"
     */
    OkexOnePosition getInstrumentPosition(String instrumentId);
    Object testInstrumentPosition(String instrumentId);



//    /**
//     * 下单
//     * @param ppOrder
//     * @return
//     */
//    String order(PpOrder ppOrder);
//
//    /**
//     * 批量下单
//     * @param ppOrders
//     * @return
//     */
//    String orders(PpOrders ppOrders);
//
//    /**
//     * 撤单
//     * @param instrumentId
//     * @param orderId
//     * @return
//     */
//    String cancelOrder(String instrumentId, String orderId);
//
//    /**
//     * 批量撤单
//     * @param instrumentId
//     * @param ppCancelOrderVO
//     * @return
//     */
//    String cancelOrders(String instrumentId, PpCancelOrderVO ppCancelOrderVO);
}
