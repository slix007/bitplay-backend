package com.bitplay.core;

import com.bitplay.service.exception.NotAuthorizedException;
import com.bitplay.service.exception.NotConnectedException;
import com.bitplay.xchange.currency.CurrencyPair;
import com.bitplay.xchange.dto.account.AccountInfo;
import io.reactivex.Observable;

/**
 * Created by Sergey Shurmin on 5/8/17.
 */
public interface StreamingAccountService {

    /**
     * Get an order book representing the current offered exchange rates (market depth). Emits {@link NotConnectedException}
     * When not connected to the WebSocket API. Emits {@link NotAuthorizedException} When not authorized.
     *
     * @return {@link Observable} that emits {@link AccountInfo} when exchange sends the update.
     */
    Observable<AccountInfo> getAccountInfoObservable(CurrencyPair currencyPair, Object... args);
}
