package com.bitplay.okex.v5;

import com.bitplay.okex.v5.client.ApiCredentialsV5;
import lombok.Data;

@Data
public class ApiConfigurationV5 {

    public static final String JSON_CONTENT_TYPE = "application/json";


    private ApiCredentialsV5 apiCredentials;

    /**
     * Rest api endpoint url.
     */
    private String endpoint;

    /**
     * Host connection timeout sec.
     */
    private long connectTimeout;
    /**
     * The host reads the information timeout sec.
     */
    private long readTimeout;
    /**
     * The host writes the information timeout sec.
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
