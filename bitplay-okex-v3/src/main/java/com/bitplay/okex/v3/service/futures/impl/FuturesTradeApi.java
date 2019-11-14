package com.bitplay.okex.v3.service.futures.impl;

import com.bitplay.okex.v3.dto.futures.param.ClosePosition;
import com.bitplay.okex.v3.dto.futures.param.LeverageCross;
import com.bitplay.okex.v3.dto.futures.param.Order;
import com.bitplay.okex.v3.dto.futures.result.Account;
import com.bitplay.okex.v3.dto.futures.result.Accounts;
import com.bitplay.okex.v3.dto.futures.result.ClosePositionResult;
import com.bitplay.okex.v3.dto.futures.result.LeverageResult;
import com.bitplay.okex.v3.dto.futures.result.OkexAllPositions;
import com.bitplay.okex.v3.dto.futures.result.OkexOnePosition;
import com.bitplay.okex.v3.dto.futures.result.OpenOrdersResult;
import com.bitplay.okex.v3.dto.futures.result.OrderDetail;
import com.bitplay.okex.v3.dto.futures.result.OrderResult;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * Futures trade api
 *
 * @author Tony Tian
 * @version 1.0.0
 * @date 2018/3/9 19:20
 */
interface FuturesTradeApi {

    // Rate Limit: 5 requests per 2 seconds
    @GET("/api/futures/v3/position")
    Call<OkexAllPositions> getPositions();

    // Rate Limit: 20 requests per 2 seconds
    @GET("/api/futures/v3/{instrument_id}/position")
    Call<OkexOnePosition> getInstrumentPosition(@Path("instrument_id") String instrumentId);

    @GET("/api/futures/v3/{instrument_id}/position")
    Call<Object> getInstrumentPositionTest(@Path("instrument_id") String instrumentId);

    // Rate Limit: once per 10 seconds
    @GET("/api/futures/v3/accounts")
    Call<Accounts> getAccounts();

    // Rate Limit: 20 requests per 2 seconds
    @GET("/api/futures/v3/accounts/{currency}")
    Call<Account> getAccountsByCurrency(@Path("currency") String currency);

    @GET("/api/futures/v3/accounts/{currency}")
    Call<Object> testAccount(@Path("currency") String currency);

//
//    @GET("/api/futures/v3/accounts/{currency}/ledger")
//    Call<JSONArray> getAccountsLedgerByCurrency(@Path("currency") String currency);
//
//    @GET("/api/futures/v3/accounts/{instrument_id}/holds")
//    Call<JSONObject> getAccountsHoldsByInstrumentId(@Path("instrument_id") String instrumentId);

    @POST("/api/futures/v3/order")
    Call<OrderResult> order(@Body Order order);

//    @POST("/api/futures/v3/orders")
//    Call<JSONObject> orders(@Body JSONObject orders);
//
    @POST("/api/futures/v3/cancel_order/{instrument_id}/{order_id}")
    Call<OrderResult> cancelOrder(@Path("instrument_id") String instrumentId, @Path("order_id") String orderId);

    @POST("/api/futures/v3/close_position")
    Call<ClosePositionResult> closePosition(@Body ClosePosition closePosition);
//
//    @POST("/api/futures/v3/cancel_batch_orders/{instrument_id}")
//    Call<JSONObject> cancelOrders(@Path("instrument_id") String instrumentId, @Body JSONObject order_ids);
//
//    @GET("/api/futures/v3/orders/{instrument_id}")
//    Call<OrderDetail> getOrders(@Path("instrument_id") String instrumentId, @Query("status") int status,
//            @Query("from") int from, @Query("to") int to, @Query("limit") int limit);
//
    @GET("/api/futures/v3/orders/{instrument_id}")
    Call<OpenOrdersResult> getOrdersWithState(@Path("instrument_id") String instrumentId, @Query("status") int status);
//
    // Rate limit: 40 requests per 2 seconds
    @GET("/api/futures/v3/orders/{instrument_id}/{order_id}")
    Call<OrderDetail> getOrder(@Path("instrument_id") String instrumentId, @Path("order_id") String orderId);
//
//    @GET("/api/futures/v3/fills")
//    Call<JSONArray> getFills(@Query("instrument_id") String instrumentId, @Query("order_id") String orderId,
//            @Query("from") int before, @Query("to") int after, @Query("limit") int limit);
//
    @GET("/api/futures/v3/accounts/{currency}/leverage")
    Call<LeverageResult> getLeverRate(@Path("currency") String currency);

//    @POST("/api/futures/v3/accounts/{currency}/leverage")
//    Call<JSONObject> changeLeverageOnFixed(@Path("currency") String currency,
//            @Body JSONObject changeLeverage);
//
    @POST("/api/futures/v3/accounts/{currency}/leverage")
    Call<LeverageResult> changeLeverageOnCross(@Path("currency") String currency,
            @Body LeverageCross changeLeverage);

}
