package com.bitplay.okex.v3;

import com.bitplay.okex.v3.client.ApiCredentials;
import lombok.Data;

@Data
public class ApiConfiguration {

    public static final String API_BASE_URL = "https://www.okex.com";
    public static final String JSON_CONTENT_TYPE = "application/json";


    private ApiCredentials apiCredentials;

    /**
     * Rest api endpoint url.
     */
    private String endpoint;

    /**
     * Host connection timeout.
     */
    private long connectTimeout;
    /**
     * The host reads the information timeout.
     */
    private long readTimeout;
    /**
     * The host writes the information timeout.
     */
    private long writeTimeout;
    /**
     * Failure reconnection, default true.
     */
    private boolean retryOnConnectionFailure;

    /**
     * The print api information.
     */
    private boolean print;


}
