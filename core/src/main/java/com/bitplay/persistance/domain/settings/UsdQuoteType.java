package com.bitplay.persistance.domain.settings;

/**
 * Created by Sergey Shurmin on 11/28/17.
 */
public enum UsdQuoteType {

    //  USD index quote: AVG, Bitmex, Okex, Index Bitmex, Index Okex.
    //1) Курс Bitmex: Среднее арифметическое между bid[1] и ask[1]
    //2) Курс Okex: Среднее арифметическое между bid[1] и ask[1]
    //3) Курс AVG: Среднее арифметическое между 1) и 2)
    //4) Курс Index Bitmex: Значение Index price у Bitmex
    //5) Курс Index Okex: Значение Index price у Okex
    LEFT,
    RIGHT,
    AVG,
    INDEX_LEFT,
    INDEX_RIGHT,
}
