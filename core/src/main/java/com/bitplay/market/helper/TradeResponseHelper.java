package com.bitplay.market.helper;

import com.bitplay.market.model.TradeResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import lombok.NonNull;

public class TradeResponseHelper {

    public String parseHttpErrorCodeBtm(String errorCode) {
        if (errorCode.contains("Account has insufficient Available Balance")) {
            return TradeResponse.INSUFFICIENT_BALANCE;
        }
        return errorCode;
    }

    private String parseHttpErrorCodeOk(String errorCode) {
        // 32015 :                                                  // margin ratio is lower than 100% before opening positions 32015
        // 32016 : Risk rate lower than 100% after opening position // margin ratio is lower than 100% after opening position   32016
        //                                                          // Risk ratio too high,Margin ratio too low when placing order	35008
        //                                                          // Account risk too high	35053
        //                                                          // Insufficient cross margin	35052
        //                                                          // Insufficient account balance	35055
        //                                                          //
        if (errorCode.contains("32015")
                || errorCode.contains("32016")
                || errorCode.contains("35008")
                || errorCode.contains("35052")
                || errorCode.contains("35055")
        ) {
            return TradeResponse.INSUFFICIENT_BALANCE;
        }
        return errorCode;
    }

    public boolean errorInsufficientFunds(@NonNull String errorCode) {
        if (parseHttpErrorCodeBtm(errorCode).equals(TradeResponse.INSUFFICIENT_BALANCE)
                || parseHttpErrorCodeOk(errorCode).equals(TradeResponse.INSUFFICIENT_BALANCE)) {
            return true;
        }

        return false;
    }

}
