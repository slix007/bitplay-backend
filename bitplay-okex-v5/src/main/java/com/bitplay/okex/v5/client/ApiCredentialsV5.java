package com.bitplay.okex.v5.client;

import com.bitplay.okex.v5.ApiConfigurationV5;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ApiCredentialsV5 {

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

    private String sslUrl;


    public ApiCredentialsV5(ApiConfigurationV5 config) {
        final ApiCredentialsV5 cred = config.getApiCredentials();
        this.apiKey = cred.getApiKey();
        this.secretKey = cred.getSecretKey();
        this.passphrase = cred.getPassphrase();
        this.sslUrl = config.getEndpoint();
    }
}
