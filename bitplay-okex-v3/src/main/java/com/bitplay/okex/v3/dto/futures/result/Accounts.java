package com.bitplay.okex.v3.dto.futures.result;

import lombok.Data;

@Data
public class Accounts {
    // {
    //      "info":{
    //          "btc": {}
    //          "eth": {}
    //      }
    // }

    private AccountsInfo info;

    @Data
    public static class AccountsInfo {

        private Account btc;
        private Account eth;
    }
}
