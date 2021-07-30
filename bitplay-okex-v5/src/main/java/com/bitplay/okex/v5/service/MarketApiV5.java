package com.bitplay.okex.v5.service;

import com.bitplay.okex.v5.dto.result.Book;
import com.bitplay.okex.v5.dto.result.Instrument;
import com.fasterxml.jackson.databind.util.JSONPObject;
import com.google.gson.JsonObject;
import java.util.List;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

interface MarketApiV5 {

//    @GET("/api/general/v3/time")
//    Call<ServerTime> getServerTime();
//
//    @GET("/api/futures/v3/rate")
//    Call<ExchangeRate> getExchangeRate();

//    @GET("/api/v5/public/instruments")
//    Call<List<Instrument>> getInstruments();
    @GET("/api/v5/public/instruments")
    Call<JsonObject> getInstrumentBook(
            @Query("instType") String instType
    );
//
//    @GET("/api/v5/market/books")
//    Call<JSONObject> getBook(
//            @Query("instId") String instrumentId,
//            @Query("sz") Integer sz
//    );

    @GET("/api/v5/market/books")
    Call<Book> getBook(
            @Query("instId") String instrumentId,
            @Query("sz") Integer sz
    );


    //
//    @GET("/api/futures/v3/instruments/currencies")
//    Call<List<Currencies>> getCurrencies();
//
    @GET("/api/v5/public/instruments")
    Call<JsonObject> getInstrumentBook(
            @Query("instType") String instType,
            @Query("instId") String instrumentId
    );

    // Rate limit：20/2s
//    @GET("/api/swap/v3/instruments/{instrument_id}/funding_time")
//    Call<SwapFundingTime> getSwapFundingTime(@Path("instrument_id") String instrumentId);
//
//    @GET("/api/futures/v3/instruments/{instrument_id}/ticker")
//    Call<Ticker> getInstrumentTicker(@Path("instrument_id") String instrumentId);
//
//    @GET("/api/futures/v3/instruments/ticker")
//    Call<List<Ticker>> getAllInstrumentTicker();
//
//    @GET("/api/futures/v3/instruments/{instrument_id}/trades")
//    Call<List<Trades>> getInstrumentTrades(@Path("instrument_id") String instrumentId, @Query("from") int from, @Query("to") int to, @Query("limit") int limit);
//
//    @GET("/api/futures/v3/instruments/{instrument_id}/candles")
//    Call<JSONArray> getInstrumentCandles(@Path("instrument_id") String instrumentId, @Query("start") String start, @Query("end") String end,
//            @Query("granularity") String granularity);
//
//    @GET("/api/futures/v3/instruments/{instrument_id}/index")
//    Call<Index> getInstrumentIndex(@Path("instrument_id") String instrumentId);
//
    // not in use
//    @GET("/api/swap/v3/instruments/{instrument_id}/estimated_price")
//    Call<EstimatedPrice> getInstrumentEstimatedPrice(@Path("instrument_id") String instrumentId);
//
//    @GET("/api/futures/v3/instruments/{instrument_id}/open_interest")
//    Call<Holds> getInstrumentHolds(@Path("instrument_id") String instrumentId);
//
//    @GET("/api/futures/v3/instruments/{instrument_id}/price_limit")
//    Call<PriceLimit> getInstrumentPriceLimit(@Path("instrument_id") String instrumentId);
//
//    @GET("/api/futures/v3/instruments/{instrument_id}/liquidation")
//    Call<List<Liquidation>> getInstrumentLiquidation(@Path("instrument_id") String instrumentId, @Query("status") int status
//            , @Query("from") int from, @Query("to") int to, @Query("limit") int limit);
//
//    @GET("/api/futures/v3/instruments/{instrument_id}/mark_price")
//    Call<JSONObject> getMarkPrice(@Path("instrument_id") String instrumentId);
}
