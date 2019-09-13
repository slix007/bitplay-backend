package com.bitplay.okex.v3.service.futures.impl;

import com.bitplay.okex.v3.dto.futures.param.LeverageCross;
import com.bitplay.okex.v3.dto.futures.param.Order;
import com.bitplay.okex.v3.dto.futures.result.Accounts;
import com.bitplay.okex.v3.dto.futures.result.LeverageResult;
import com.bitplay.okex.v3.dto.futures.result.OkexPosition;
import com.bitplay.okex.v3.dto.futures.result.OkexPositions;
import com.bitplay.okex.v3.dto.futures.result.OrderResult;
import com.fasterxml.jackson.databind.util.JSONPObject;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

/**
 * Futures trade api
 *
 * @author Tony Tian
 * @version 1.0.0
 * @date 2018/3/9 19:20
 */
interface FuturesTradeApi {

    @GET("/api/futures/v3/position")
    Call<OkexPositions> getPositions();
//
//    @GET("/api/futures/v3/{instrument_id}/position")
//    Call<JSONObject> getInstrumentPosition(@Path("instrument_id") String instrumentId);

    @GET("/api/futures/v3/accounts")
    Call<Accounts> getAccounts();

//    @GET("/api/futures/v3/accounts/{currency}")
//    Call<JSONObject> getAccountsByCurrency(@Path("currency") String currency);
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
//    @POST("/api/futures/v3/cancel_order/{instrument_id}/{order_id}")
//    Call<JSONObject> cancelOrder(@Path("instrument_id") String instrumentId, @Path("order_id") String orderId);
//
//    @POST("/api/futures/v3/cancel_batch_orders/{instrument_id}")
//    Call<JSONObject> cancelOrders(@Path("instrument_id") String instrumentId, @Body JSONObject order_ids);
//
//    @GET("/api/futures/v3/orders/{instrument_id}")
//    Call<JSONObject> getOrders(@Path("instrument_id") String instrumentId, @Query("status") int status,
//            @Query("from") int from, @Query("to") int to, @Query("limit") int limit);
//
//    @GET("/api/futures/v3/orders/{instrument_id}/{order_id}")
//    Call<JSONObject> getOrder(@Path("instrument_id") String instrumentId, @Path("order_id") String orderId);
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
