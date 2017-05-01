package com.bitplay.marketapi;

import io.swagger.client.*;
import io.swagger.client.auth.*;
import io.swagger.client.model.*;
import io.swagger.client.api.APIKeyApi;

import java.io.File;
import java.util.*;

public class APIKeyApiExample {

    public static void main(String[] args) {

        APIKeyApi apiInstance = new APIKeyApi();
        String apiKeyID = "apiKeyID_example"; // String | API Key ID (public component).
        try {
            APIKey result = apiInstance.aPIKeyDisable(apiKeyID);
            System.out.println(result);
        } catch (ApiException e) {
            System.err.println("Exception when calling APIKeyApi#aPIKeyDisable");
            e.printStackTrace();
        }
    }
}