package com.bitplay.okex.v5.service;

import com.bitplay.okex.v5.dto.result.Book;
import com.bitplay.okex.v5.dto.result.EstimatedPriceDto;
import com.bitplay.okex.v5.dto.result.Instruments;
import com.bitplay.okex.v5.dto.result.SwapFundingTime;
import com.google.gson.JsonObject;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

interface MarketApiV5 {

    @GET("/api/v5/public/instruments")
    Call<Instruments> getInstruments(
            @Query("instType") String instType
    );

    @GET("/api/v5/market/books")
    Call<Book> getBook(
            @Query("instId") String instrumentId,
            @Query("sz") Integer sz
    );

    @GET("/api/v5/public/instruments")
    Call<JsonObject> getInstrumentBook(
            @Query("instType") String instType,
            @Query("instId") String instrumentId
    );

    // Rate Limit: 20 requests per 2 seconds
    @GET("/api/v5/public/funding-rate")
    Call<SwapFundingTime> getSwapFundingTime(@Query("instId") String instrumentId);

    @GET("/api/v5/public/estimated-price")
    Call<EstimatedPriceDto> getInstrumentEstimatedPrice(@Query("instId") String instrumentId);
}
