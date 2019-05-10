package com.bitplay.okex.v3.client;

import com.bitplay.okex.v3.ApiConfiguration;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ApiCredentials {

    /**
     * The user's secret key provided by OKEx.
     */
    private String apiKey;
    /**
     * The private key used to sign your request data.
     */
    private String secretKey;
    /**
     * The Passphrase will be provided by you to further secure your API access.
     */
    private String passphrase;


    public ApiCredentials(ApiConfiguration config) {
        final ApiCredentials cred = config.getApiCredentials();
        this.apiKey = cred.getApiKey();
        this.secretKey = cred.getSecretKey();
        this.passphrase = cred.getPassphrase();
    }
}
