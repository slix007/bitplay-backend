package com.bitplay.okex.v5.service;

import com.bitplay.okex.v5.dto.SwapLeverageCross;
import com.bitplay.okex.v5.dto.param.Order;
import com.bitplay.okex.v5.dto.result.Account;
import com.bitplay.okex.v5.dto.result.Accounts;
import com.bitplay.okex.v5.dto.result.LeverageResult;
import com.bitplay.okex.v5.dto.result.OkexOnePositionV5;
import com.bitplay.okex.v5.dto.result.OkexSwapAllPositions;
import com.bitplay.okex.v5.dto.result.OpenOrdersResult;
import com.bitplay.okex.v5.dto.result.OrderDetail;
import com.bitplay.okex.v5.dto.result.OrderResult;
import com.bitplay.okex.v5.dto.result.SwapAccounts;
import com.google.gson.JsonObject;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * Futures trade api
 */
interface TradeApi {

    // Rate Limit: 5 requests per 2 seconds
    @GET("/api/swap/v3/position")
    Call<OkexSwapAllPositions> getPositions();

    @GET("/api/v5/account/positions")
    Call<JsonObject> getInstrumentPositionTest(
            @Query("instType") String instType,
            @Query("instId") String instId
    );

    // Rate Limit: 20 requests per 2 seconds
    @GET("/api/v5/account/positions")
    Call<OkexOnePositionV5> getInstrumentPosition(
            @Query("instType") String instType,
            @Query("instId") String instId
    );

    @GET("/api/swap/v3/{instrument_id}/position")
    Call<Object> testInstrumentPosition(@Path("instrument_id") String instrumentId);

    // Rate Limit: once per 10 seconds
    @GET("/api/swap/v3/accounts")
    Call<Accounts> getAccounts();

    // Rate Limit: 20 requests per 2 seconds
    @GET("/api/swap/v3/{instrumentId}/accounts")
    Call<SwapAccounts> getAccountByInstrumentId(@Path("instrumentId") String instrumentId);

    @GET("/api/swap/v3/{instrumentId}/accounts")
    Call<Object> testAccountByInstrumentId(@Path("instrumentId") String instrumentId);

    // Rate Limit: 10 requests per 2 seconds
    /**
     * Single currency or multiple currencies (no more than 20) separated with comma, e.g. BTC or BTC,ETH.
     */
    @GET("/api/v5/account/balance")
    Call<Account> getBalance(@Query("ccy") String currency);

    @GET("/api/v5/account/balance")
    Call<JsonObject> getBalance();

    @GET("/api/v5/asset/balances")
    Call<JsonObject> getAssetBalances(@Query("ccy") String currency);

    @GET("/api/v5/asset/balances")
    Call<JsonObject> getAssetBalances();

//
//    @GET("/api/futures/v3/accounts/{currency}/ledger")
//    Call<JSONArray> getAccountsLedgerByCurrency(@Path("currency") String currency);
//
//    @GET("/api/futures/v3/accounts/{instrument_id}/holds")
//    Call<JSONObject> getAccountsHoldsByInstrumentId(@Path("instrument_id") String instrumentId);

    @POST("/api/swap/v3/order")
    Call<OrderResult> order(@Body Order order);

    //    @POST("/api/futures/v3/orders")
//    Call<JSONObject> orders(@Body JSONObject orders);
//
    @POST("/api/swap/v3/cancel_order/{instrument_id}/{order_id}")
    Call<OrderResult> cancelOrder(@Path("instrument_id") String instrumentId, @Path("order_id") String orderId);

    //    @POST("/api/futures/v3/cancel_batch_orders/{instrument_id}")
//    Call<JSONObject> cancelOrders(@Path("instrument_id") String instrumentId, @Body JSONObject order_ids);
//
//    @GET("/api/futures/v3/orders/{instrument_id}")
//    Call<OrderDetail> getOrders(@Path("instrument_id") String instrumentId, @Query("status") int status,
//            @Query("from") int from, @Query("to") int to, @Query("limit") int limit);
//
    @GET("/api/swap/v3/orders/{instrument_id}")
    Call<OpenOrdersResult> getOrdersWithState(@Path("instrument_id") String instrumentId, @Query("status") int status);

    //
    // Rate limit: 40 requests per 2 seconds
    @GET("/api/swap/v3/orders/{instrument_id}/{order_id}")
    Call<OrderDetail> getOrder(@Path("instrument_id") String instrumentId, @Path("order_id") String orderId);

    //
//    @GET("/api/futures/v3/fills")
//    Call<JSONArray> getFills(@Query("instrument_id") String instrumentId, @Query("order_id") String orderId,
//            @Query("from") int before, @Query("to") int after, @Query("limit") int limit);
//
    //Rate limit：5 requests per 2 seconds
    @GET("/api/swap/v3/accounts/{instrumentId}/settings")
    Call<LeverageResult> getLeverRate(@Path("instrumentId") String instrumentId);

    //    @POST("/api/futures/v3/accounts/{currency}/leverage")
//    Call<JSONObject> changeLeverageOnFixed(@Path("currency") String currency,
//            @Body JSONObject changeLeverage);
//
    @POST("/api/swap/v3/accounts/{instrumentId}/leverage")
    Call<LeverageResult> changeLeverageOnCross(@Path("instrumentId") String instrumentId,
            @Body SwapLeverageCross changeLeverage);

}
