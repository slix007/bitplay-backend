package com.bitplay.externalapi;

import lombok.Data;

@Data
public class FplayExchange {
    protected final PublicApi publicApi;
    protected final PrivateApi privateApi;
}
