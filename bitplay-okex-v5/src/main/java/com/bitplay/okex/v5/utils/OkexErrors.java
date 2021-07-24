package com.bitplay.okex.v5.utils;

public class OkexErrors {

    public static String getErrorMessage(int errorCode) {
        //TODO https://www.okex.com/docs/en/#error-Error_Code

        switch (errorCode) {

            case (35029):
                return "order does not exist";
            case (33014):
                return "order does not exist(order canceled already. Invalid order number)";
            case (10000):
                return "Required field can not be null";

            default:
                return "Unknown error";
        }
    }
}
