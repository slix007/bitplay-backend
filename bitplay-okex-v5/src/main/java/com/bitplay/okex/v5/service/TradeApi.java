package com.bitplay.okex.v5.service;

import com.bitplay.okex.v5.dto.ChangeLeverRequest;
import com.bitplay.okex.v5.dto.param.Order;
import com.bitplay.okex.v5.dto.param.OrderAmendRequest;
import com.bitplay.okex.v5.dto.param.OrderCnlRequest;
import com.bitplay.okex.v5.dto.result.Account;
import com.bitplay.okex.v5.dto.result.LeverageResult;
import com.bitplay.okex.v5.dto.result.OkexOnePositionV5;
import com.bitplay.okex.v5.dto.result.OrderResult;
import com.bitplay.okex.v5.dto.result.OrdersDetailResult;
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

    //Rate Limit: 60 requests per 2 seconds
    @POST("/api/v5/trade/order")
    Call<OrderResult> placeOrder(@Body Order order);

    @POST("/api/v5/trade/order")
    Call<Object> placeOrderTest(@Body Order order);

    @POST("/api/v5/trade/cancel-order")
    Call<OrderResult> cancelOrder(@Body OrderCnlRequest order);

    // Rate Limit: 60 requests per 2 seconds
    @POST("/api/v5/trade/amend-order")
    Call<OrderResult> amendOrder(@Body OrderAmendRequest order);

    // Rate Limit: 20 requests per 2 seconds
    @GET("/api/v5/trade/orders-pending")
    Call<OrdersDetailResult> getOrdersWithState(
            @Query("instType") String instType,
            @Query("instId") String instId
            //market: market order
            //limit: limit order
            //post_only: Post-only order
            //fok: Fill-or-kill order
            //ioc: Immediate-or-cancel order
            //Optimal_limit_ioc :Market order with immediate-or-cancel order
//            @Query("ordType") String ordType
    );

    //
    // Rate Limit: 60 requests per 2 seconds
    @GET("/api/v5/trade/order")
    Call<OrdersDetailResult> getOrder(
            @Query("instId") String instId,
            @Query("ordId") String ordId
    );

    //Rate Limit: 20 requests per 2 seconds
    @GET("/api/v5/account/leverage-info")
    Call<LeverageResult> getLeverRate(
            @Query("instId") String instrumentId,
            //Margin mode
            //cross isolated
            @Query("mgnMode") String mgnMode
    );

    // Rate Limit: 5 requests per 2 seconds
    @POST("/api/v5/account/set-leverage")
    Call<LeverageResult> changeLeverageOnCross(@Body ChangeLeverRequest changeLeverage);

}
