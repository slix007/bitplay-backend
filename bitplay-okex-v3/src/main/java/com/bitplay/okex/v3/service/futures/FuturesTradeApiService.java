package com.bitplay.okex.v3.service.futures;

import com.bitplay.okex.v3.dto.futures.param.Order;
import com.bitplay.okex.v3.dto.futures.result.Account;
import com.bitplay.okex.v3.dto.futures.result.Accounts;
import com.bitplay.okex.v3.dto.futures.result.LeverageResult;
import com.bitplay.okex.v3.dto.futures.result.OkexAllPositions;
import com.bitplay.okex.v3.dto.futures.result.OkexOnePosition;
import com.bitplay.okex.v3.dto.futures.result.OrderDetail;
import com.bitplay.okex.v3.dto.futures.result.OrderResult;
import java.util.List;

/**
 * Futures Trade API Service
 *
 * @author Tony Tian
 * @version 1.0.0
 * @date 2018/3/9 18:52
 */
public interface FuturesTradeApiService {

    /**
     * Get all of futures contract position list
     */
    OkexAllPositions getPositions();

    /**
     * Get the futures contract product position
     *
     * @param instrumentId The id of the futures contract eg: BTC-USD-0331"
     */
    OkexOnePosition getInstrumentPosition(String instrumentId);

    /**
     * Get all of futures contract account list
     */
    Accounts getAccounts();

//    /**
//     * Get the futures contract currency account
//     *
//     * @param currency {@link com.okcoin.commons.okex.open.api.enums.FuturesCurrenciesEnum}
//     *                 eg: FuturesCurrenciesEnum.BTC.name()
//     */
    Account getAccountsByCurrency(String currency);
//
//    /**
//     * Get the futures contract currency ledger
//     *
//     * @param currency {@link com.okcoin.commons.okex.open.api.enums.FuturesCurrenciesEnum}
//     *                 eg: FuturesCurrenciesEnum.BTC.name()
//     */
//    JSONArray getAccountsLedgerByCurrency(String currency);
//
//    /**
//     * Get the futures contract product holds
//     *
//     * @param instrumentId The id of the futures contract eg: BTC-USD-0331"
//     */
//    JSONObject getAccountsHoldsByInstrumentId(String instrumentId);

    /**
     * Create a new order
     */
    OrderResult order(Order order);

//    /**
//     * Batch create new order.(Max of 5 orders are allowed per request))
//     */
//    JSONObject orders(Orders orders);
//
    /**
     * Cancel the order
     *  @param instrumentId The id of the futures contract eg: BTC-USD-0331"
     * @param orderId   the order id provided by okex.com eg: 372238304216064
     */
    OrderResult cancelOrder(String instrumentId, String orderId);
//
//    /**
//     * Batch Cancel the orders of this product id
//     *
//     * @param instrumentId The id of the futures contract eg: BTC-USD-0331"
//     */
//    JSONObject cancelOrders(String instrumentId, CancelOrders cancelOrders);
//
//    /**
//     * Get all of futures contract order list
//     *
//     * @param status   Order status: 0: waiting for transaction 1: 1: part of the deal 2: all transactions.
//     * @param from    Paging content after requesting this id .
//     * @param to     Paging content prior to requesting this id.
//     * @param limit    Number of results per request. Maximum 100. (default 100)
//     *                 {@link com.okcoin.commons.okex.open.api.bean.futures.CursorPageParams}
//     * @return
//     */
//    JSONObject getOrders(String instrument_id, int status, int from, int to, int limit);
//
    /**
     * Get all of futures contract a order by order id
     *
     * @param instrumentId  eg: futures id
     */
    OrderDetail getOrder(String instrumentId, String orderId);
//
//    /**
//     * Get all of futures contract transactions.
//     *
//     * @param instrumentId The id of the futures contract eg: BTC-USD-0331"
//     * @param orderId   the order id provided by okex.com eg: 372238304216064
//     * @param from    Paging content after requesting this id .
//     * @param to     Paging content prior to requesting this id.
//     * @param limit     Number of results per request. Maximum 100. (default 100)
//     *                  {@link com.okcoin.commons.okex.open.api.bean.futures.CursorPageParams}
//     * @return
//     */
//    JSONArray getFills(String instrumentId, long orderId, int from, int to, int limit);
//
    /**
     * Get the futures LeverRate
     *
     * @param currency eg: btc
     */
    LeverageResult getInstrumentLeverRate(String currency);
//
//
//    /**
//     * Change the futures Fixed LeverRate
//     *
//     * @param currency       eg: btc
//     * @param instrumentId   eg: BTC-USD-190628
//     * @param direction      eg: long
//     * @param leverage       eg: 10
//     * @return
//     */
//    JSONObject changeLeverageOnFixed(String currency, String instrumentId, String direction, String leverage);
//
    /**
     * Change the futures Cross LeverRate
     *
     * @param currency      eg: btc
     * @param leverage      eg: 10
     * @return
     */
    LeverageResult changeLeverageOnCross(String currency, String leverage);


    List<OrderDetail> getOpenOrders(String instrumentId);

}
