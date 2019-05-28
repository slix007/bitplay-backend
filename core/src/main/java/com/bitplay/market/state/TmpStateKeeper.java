package com.bitplay.market.state;

import com.bitplay.utils.Utils;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.schedulers.Schedulers;
import java.math.BigDecimal;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.knowm.xchange.dto.account.Position;

public class TmpStateKeeper {

    private final ExecutorService stateUpdateExecutor;
    private final Scheduler stateUpdateScheduler;

    //    protected volatile Position position = new Position(null, null, null, BigDecimal.ZERO, "");
//    protected volatile Position positionXBTUSD = new Position(null, null, null, BigDecimal.ZERO, "");
//    protected volatile Affordable affordable = new Affordable();
    private final TmpState tmpState = new TmpState();

    public TmpStateKeeper(String name) {
        stateUpdateExecutor = Executors.newSingleThreadExecutor(new NamedThreadFactory(name + "-tmpState-updater"));
        stateUpdateScheduler = Schedulers.from(stateUpdateExecutor);
    }

    public Future asyncUpdate(Callable<Object> updateTask) {
        return stateUpdateExecutor.submit(updateTask);
    }

    public void asyncUpdatePos(Position pUpdate) {

        return;
    }
//
//    private Completable recalcAffordableContracts() {
//        return Completable.fromAction(() -> {
//            final BigDecimal reserveBtc = arbitrageService.getParams().getReserveBtc2();
//            final BigDecimal volPlan = settingsRepositoryService.getSettings().getPlacingBlocks().getFixedBlockOkex();
////        final BigDecimal volPlan = arbitrageService.getParams().getBlock2();
//
//            if (accountInfoContracts != null && position != null && Utils.orderBookIsFull(orderBook)) {
//                final BigDecimal available = accountInfoContracts.getAvailable();
//                final BigDecimal equity = accountInfoContracts.geteLast();
//
//                final BigDecimal bestAsk = Utils.getBestAsks(orderBook, 1).get(0).getLimitPrice();
//                final BigDecimal bestBid = Utils.getBestBids(orderBook, 1).get(0).getLimitPrice();
//                final BigDecimal leverage = position.getLeverage();
//
//                if (available != null && equity != null && leverage != null && position.getPositionLong() != null && position.getPositionShort() != null) {
//
////                if (available.signum() > 0) {
////                if (orderType.equals(Order.OrderType.BID) || orderType.equals(Order.OrderType.EXIT_ASK)) {
//                    BigDecimal affordableContractsForLong;
//                    final BigDecimal usdInContract = BigDecimal.valueOf(this.usdInContract);
//                    if (position.getPositionShort().signum() != 0) { // there are sells
//                        if (volPlan.compareTo(position.getPositionShort()) != 1) {
//                            affordableContractsForLong = (position.getPositionShort().subtract(position.getPositionLong()).add(
//                                    (equity.subtract(reserveBtc)).multiply(bestAsk).multiply(leverage).divide(usdInContract, 0, BigDecimal.ROUND_DOWN)
//                            )).setScale(0, BigDecimal.ROUND_DOWN);
//                        } else {
//                            affordableContractsForLong = (available.subtract(reserveBtc)).multiply(bestAsk).multiply(leverage)
//                                    .divide(usdInContract, 0, BigDecimal.ROUND_DOWN);
//                        }
//                        if (affordableContractsForLong.compareTo(position.getPositionShort()) == -1) {
//                            affordableContractsForLong = position.getPositionShort();
//                        }
//                    } else { // no sells
//                        affordableContractsForLong = (available.subtract(reserveBtc)).multiply(bestAsk).multiply(leverage)
//                                .divide(usdInContract, 0, BigDecimal.ROUND_DOWN);
//                    }
//                    affordable.setForLong(affordableContractsForLong);
////                }
//
////                if (orderType.equals(Order.OrderType.ASK) || orderType.equals(Order.OrderType.EXIT_BID)) {
//                    BigDecimal affordableContractsForShort;
//                    if (position.getPositionLong().signum() != 0) { // we have BIDs
//                        if (volPlan.compareTo(position.getPositionLong()) != 1) { // если мы хотим закрыть меньше чем есть
//                            final BigDecimal divide = (equity.subtract(reserveBtc)).multiply(bestBid.multiply(leverage))
//                                    .divide(usdInContract, 0, BigDecimal.ROUND_DOWN);
//                            affordableContractsForShort = (position.getPositionLong().subtract(position.getPositionShort()).add(
//                                    divide
//                            )).setScale(0, BigDecimal.ROUND_DOWN);
//                        } else {
//                            affordableContractsForShort = (available.subtract(reserveBtc)).multiply(bestBid).multiply(leverage)
//                                    .divide(usdInContract, 0, BigDecimal.ROUND_DOWN);
//                        }
//                        if (affordableContractsForShort.compareTo(position.getPositionLong()) == -1) {
//                            affordableContractsForShort = position.getPositionLong();
//                        }
//                    } else { // no BIDs
//                        affordableContractsForShort = ((available.subtract(reserveBtc)).multiply(bestBid).multiply(leverage))
//                                .divide(usdInContract, 0, BigDecimal.ROUND_DOWN);
//                    }
//                    affordable.setForShort(affordableContractsForShort);
////                }
////                }
//                }
//            }
//
//        });
//    }


    private void recalcLiqInfo() {

    }
}
