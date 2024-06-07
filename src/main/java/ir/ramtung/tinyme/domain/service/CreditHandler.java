package ir.ramtung.tinyme.domain.service;


import ir.ramtung.tinyme.domain.entity.*;
import org.springframework.stereotype.Service;

import java.util.LinkedList;

@Service
public class CreditHandler {
    public CreditOutCome handleTradeCredit(Order newOrder, Trade trade) {
        if (newOrder.getSide() == Side.BUY) {
            if (trade.buyerHasEnoughCredit()) {
                trade.decreaseBuyersCredit();
            } else {
                return CreditOutCome.NOT_ENOUGH;
            }
        }
        trade.increaseSellersCredit();
        return CreditOutCome.ENOUGH;
    }

    public void rollBackCredits(Order newOrder, LinkedList<Trade> trades){
        if(newOrder.getSide() == Side.BUY){
            newOrder.getBroker().increaseCreditBy(trades.stream().mapToLong(Trade::getTradedValue).sum());
            trades.forEach(trade -> trade.getSell().getBroker().decreaseCreditBy(trade.getTradedValue()));
        }
        else{
            newOrder.getBroker().decreaseCreditBy(trades.stream().mapToLong(Trade::getTradedValue).sum());
        }
    }

    public StopStatus initStopLimitOrderCredit(StopLimitOrder stopLimitOrder){
        if(stopLimitOrder.getSide() == Side.BUY &&
        !stopLimitOrder.getBroker().hasEnoughCredit(stopLimitOrder.getValue()))
            return StopStatus.NOT_ENOUGH;
        if(!stopLimitOrder.isActivatable(stopLimitOrder.getSecurity().getMarketPrice())){
            if(stopLimitOrder.getSide() == Side.BUY)
                stopLimitOrder.getBroker().decreaseCreditBy(stopLimitOrder.getValue());
            return StopStatus.NOT_ACTIVATABLE;
        }
        return StopStatus.ACTIVATABLE;
    }
}
