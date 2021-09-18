package com.bitplay.okexv5;

import com.bitplay.core.StreamingPrivateDataService;
import com.bitplay.core.dto.PositionStream;
import com.bitplay.core.helper.WsObjectMapperHelper;
import com.bitplay.okexv5.dto.InstrumentDto;
import com.bitplay.okexv5.dto.OkCoinAuthSigner;
import com.bitplay.okexv5.dto.privatedata.OkexAccountResult;
import com.bitplay.okexv5.dto.privatedata.OkExUserOrder;
import com.bitplay.okexv5.dto.privatedata.OkexPos;
import com.bitplay.okexv5.dto.request.RequestDto;
import com.bitplay.okexv5.dto.request.RequestDto.AccountRequestArgs;
import com.bitplay.okexv5.dto.request.RequestDto.OP;
import com.bitplay.okexv5.dto.request.RequestDto.PositionRequestArgs;
import com.bitplay.xchange.Exchange;
import com.bitplay.xchange.currency.CurrencyPair;
import com.bitplay.xchange.dto.account.AccountInfoContracts;
import com.bitplay.xchange.dto.trade.LimitOrder;
import com.bitplay.xchange.okcoin.FuturesContract;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by Sergey Shurmin on 6/18/17.
 */
public class OkExStreamingPrivateDataServiceV5 implements StreamingPrivateDataService {

    private static final Logger logger = LoggerFactory.getLogger(OkExStreamingPrivateDataServiceV5.class);

    private final ObjectMapper objectMapper = WsObjectMapperHelper.getObjectMapper();

    private final OkexStreamingServiceWsToRxV5 service;
    private final Exchange exchange;

    OkExStreamingPrivateDataServiceV5(OkexStreamingServiceWsToRxV5 service, Exchange exchange) {
        this.service = service;
        this.exchange = exchange;
    }

    @Override
    public Completable login() {
        final OkCoinAuthSigner authSigner = OkCoinAuthSigner.fromExchange(exchange);
        return service.doLogin(authSigner);
    }

    @Override
    public Observable<AccountInfoContracts> getAccountInfoObservable(CurrencyPair currencyPair, Object... args) {
        final String currency = currencyPair.base.toString().toUpperCase();
        final RequestDto requestDto = new RequestDto(OP.subscribe, new AccountRequestArgs("account", currency));

        final String channelName = "account/" + currency;
        final Observable<AccountInfoContracts> observable = service.subscribeChannel(channelName, requestDto)
                .observeOn(Schedulers.computation())
//                .doOnNext(d -> System.out.println("account: " + d))
                .map(s -> s.get("data"))
                .flatMap(Observable::fromIterable)
                .map(s -> s.get("details"))
//                .doOnNext(d -> System.out.println("details: " + d))
                .flatMap(Observable::fromIterable)
                .map(s -> objectMapper.treeToValue(s, OkexAccountResult.class))
                .map(result -> OkExAdapters.adaptAccountInfo(currencyPair.base, result));

        return service.isLoggedInSuccessfully()
                ? observable
                : login().andThen(observable);
    }

    @Override
    public Observable<PositionStream> getPositionObservable(Object instrumentDtoObj, Object... args) {
        final InstrumentDto instrumentDto = (InstrumentDto) instrumentDtoObj;
        final String instrumentId = instrumentDto.getInstrumentId();
        final boolean isSwap = instrumentDto.getFuturesContract() == FuturesContract.Swap;
        final String instType = isSwap ? "SWAP" : "FUTURES";

        final RequestDto requestDto = new RequestDto(OP.subscribe, new PositionRequestArgs("positions", instType, instrumentId));

        final String channelName = "positions/" + instrumentId;

        final Observable<PositionStream> observable = service.subscribeChannel(channelName, requestDto)
                .observeOn(Schedulers.computation())
//                .doOnNext(d -> System.out.println("positions: " + d))
                .map(s -> s.get("data"))
//                .flatMap(Observable::fromIterable)
//                .map(s -> s.get("holding"))
                .flatMap(Observable::fromIterable)
//                    .doOnNext(d -> System.out.println("data: " + d))
                .map(s -> objectMapper.treeToValue(s, OkexPos.class))
//                    .doOnNext(System.out::println)
                .map(OkExAdapters::adaptPos);

        return service.isLoggedInSuccessfully()
                ? observable
                : login().andThen(observable);
    }

    @Override
    public Observable<List<LimitOrder>> getTradesObservable(Object instrumentDtoObj) {
        InstrumentDto instrumentDto = (InstrumentDto) instrumentDtoObj;
        // {"op": "subscribe", "args": ["futures/order:BTC-USD-170317"]}
        final String instrumentId = instrumentDto.getInstrumentId();
        final String channelName = instrumentDto.getFuturesContract() == FuturesContract.Swap
                ? "swap/order:" + instrumentId
                : "futures/order:" + instrumentId;
        final Observable<List<LimitOrder>> observable = service.subscribeChannel(channelName)
                .observeOn(Schedulers.computation())
                .map(s -> s.get("data"))
                .map(s -> objectMapper.treeToValue(s, OkExUserOrder[].class))
//                .doOnNext(s -> System.out.println(Arrays.asList(s)))
                .map(OkExAdapters::adaptTradeResult);

        return service.isLoggedInSuccessfully()
                ? observable
                : login().andThen(observable);

    }

    // try workaround java.lang.NoSuchMethodError because of different XChange lib versions
    public Observable<OkExUserOrder[]> getTradesObservableRaw(InstrumentDto instrumentDto) {
        // {"op": "subscribe", "args": ["futures/order:BTC-USD-170317"]}
        final String instrumentId = instrumentDto.getInstrumentId();
        final String channelName = instrumentDto.getFuturesContract() == FuturesContract.Swap
                ? "swap/order:" + instrumentId
                : "futures/order:" + instrumentId;
        final Observable<OkExUserOrder[]> observable = service.subscribeChannel(channelName)
                .observeOn(Schedulers.computation())
                .map(s -> s.get("data"))
                .map(s -> objectMapper.treeToValue(s, OkExUserOrder[].class));

        return service.isLoggedInSuccessfully()
                ? observable
                : login().andThen(observable);
    }
}
