package com.crypto.quoine;

/**
 * Created by Sergey Shurmin on 3/20/17.
 */
public abstract class QuoineBase {

    protected static final String TOKEN_ID = "70800";
    protected static final String TOKEN_SECRET = "0dsZtehd7ePyibY8YM4V6SHekVzRLzT32fM7cm0hY5unDPIfVYG+ly2RSthcsa4EPZ97bth8WTHH8Mp8FVmPAQ==";
    protected static final String BASE_URL = "https://api.quoine.com";

    public abstract void doTheWork();
}
