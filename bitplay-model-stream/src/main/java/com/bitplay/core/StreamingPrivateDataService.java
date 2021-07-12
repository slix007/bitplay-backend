package com.bitplay.core;

import com.bitplay.core.dto.PositionStream;
//import com.bitplay.xchangestream.okexv3.dto.InstrumentDto;
import com.bitplay.xchange.currency.CurrencyPair;
import com.bitplay.xchange.dto.account.AccountInfoContracts;
import com.bitplay.xchange.dto.trade.LimitOrder;
import io.reactivex.Completable;
import io.reactivex.Observable;
import java.util.List;

/**
 * Channels that require login.
 * <br>
 * Created by Sergey Shurmin on 6/10/17.
 */
public interface StreamingPrivateDataService {

    Completable login();

    Observable<AccountInfoContracts> getAccountInfoObservable(CurrencyPair currencyPair, Object... args);

    Observable<PositionStream> getPositionObservable(Object instrumentDto, Object... args);

    Observable<List<LimitOrder>> getTradesObservable(Object instrumentDto);


}
