package ir.ramtung.tinyme.domain.service.control;

import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.messaging.request.MatchingState;
import org.springframework.stereotype.Component;

import java.util.LinkedList;

@Component
public class CreditControl implements MatchingControl {
    @Override
    public MatchingOutcome canTrade(Order newOrder, Trade trade) {
        if ((newOrder.getSide() == Side.SELL) || (newOrder.getSide() == Side.BUY && trade.buyerHasEnoughCredit())) {
            return MatchingOutcome.OK;
        } else return MatchingOutcome.NOT_ENOUGH_CREDIT;
    }

    @Override
    public void tradeAccepted(Order newOrder, Trade trade) {
        if (newOrder.getSide() == Side.BUY)
            trade.decreaseBuyersCredit();
        trade.increaseSellersCredit();
    }

    @Override
    public MatchingOutcome canAcceptMatching(Order order, MatchResult result) {
        if (result.remainder().getQuantity() > 0) {
            if (order.getSide() == Side.BUY) {
                if (!order.getBroker().hasEnoughCredit(order.getValue()))
                    return MatchingOutcome.NOT_ENOUGH_CREDIT;
            }
        }
        return MatchingOutcome.OK;
    }

    @Override
    public void matchingAccepted(Order order, MatchResult result) {
        if (order.getSide() == Side.BUY) {
            order.getBroker().decreaseCreditBy(order.getValue());
        }
    }

    @Override
    public void rollbackTrades(Order newOrder, LinkedList<Trade> trades) {
        if (newOrder.getSide() == Side.BUY) {
            newOrder.getBroker().increaseCreditBy(trades.stream().mapToLong(Trade::getTradedValue).sum());
            trades.forEach(trade -> trade.getSell().getBroker().decreaseCreditBy(trade.getTradedValue()));
        }
        else
            newOrder.getBroker().decreaseCreditBy(trades.stream().mapToLong(Trade::getTradedValue).sum());
    }

    @Override
    public MatchingOutcome canStartMatching(Order order) {
        if (order instanceof StopLimitOrder &&
                order.getSide() == Side.BUY &&
                !order.getBroker().hasEnoughCredit(order.getValue()))
            return MatchingOutcome.NOT_ENOUGH_CREDIT;
        return MatchingOutcome.OK;
    }

    @Override
    public void matchingStarted(Order order) {
        if (order.getSide() == Side.BUY) {
            if (order instanceof StopLimitOrder stopLimitOrder && !stopLimitOrder.isActivatable(order.getSecurity().getMarketPrice()))
                order.getBroker().decreaseCreditBy(order.getValue());
            if (order.getSecurity().getMatchingState() == MatchingState.CONTINUOUS && order.getStatus() == OrderStatus.ACTIVATED)
                order.getBroker().increaseCreditBy(order.getValue());
        }
    }

    @Override
    public void marketOpenned(Order order) {
        order.getBroker().increaseCreditBy(order.getValue());
    }
}
