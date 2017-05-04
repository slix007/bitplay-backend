package org.knowm.xchange.bitmex;

import io.swagger.client.ApiClient;

/**
 * Created by Sergey Shurmin on 5/3/17.
 */
public class Bitmex {

    protected final ApiClient apiClient = new ApiClient();

    Bitmex() {
        apiClient.setBasePath("https://www.bitmex.com/api/v1");
    }

}
