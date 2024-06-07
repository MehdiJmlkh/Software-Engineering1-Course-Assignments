package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

@Service
public class Matcher {
    CreditHandler creditHandler = new CreditHandler();
    public MatchResult match(Order newOrder, int openingPrice) {
        OrderBook orderBook = newOrder.getSecurity().getOrderBook();
        LinkedList<Trade> trades = new LinkedList<>();
        while (orderBook.hasOrderOfType(newOrder.getSide().opposite()) && newOrder.getQuantity() > 0) {
            Order matchingOrder = orderBook.matchWithFirst(newOrder);
            if (matchingOrder == null)
                break;
            int price = openingPrice == 0 ? matchingOrder.getPrice() : openingPrice;
            Trade trade = new Trade(newOrder.getSecurity(), price, Math.min(newOrder.getQuantity(), matchingOrder.getQuantity()), newOrder, matchingOrder);
            if(creditHandler.handleTradeCredit(newOrder, trade) == CreditOutCome.NOT_ENOUGH) {
                rollbackTrades(newOrder, trades);
                return MatchResult.notEnoughCredit();
            }
            trades.add(trade);
            if (newOrder.getQuantity() >= matchingOrder.getQuantity()) {
                newOrder.decreaseQuantity(matchingOrder.getQuantity());
                orderBook.removeFirst(matchingOrder.getSide());
                if (matchingOrder instanceof IcebergOrder icebergOrder) {
                    icebergOrder.decreaseQuantity(matchingOrder.getQuantity());
                    icebergOrder.replenish();
                    if (icebergOrder.getQuantity() > 0)
                        orderBook.enqueue(icebergOrder);
                }
            } else {
                matchingOrder.decreaseQuantity(newOrder.getQuantity());
                newOrder.makeQuantityZero();
            }
        }
        if (newOrder instanceof IcebergOrder icebergOrder && newOrder.getStatus() != OrderStatus.NEW && newOrder.getQuantity() == 0)
            icebergOrder.replenish();
        return MatchResult.executed(newOrder, trades);
    }

    public MatchResult match(Order newOrder) {
        return match(newOrder, 0);
    }

    private void rollbackTrades(Order newOrder, LinkedList<Trade> trades) {
        creditHandler.rollBackCredits(newOrder, trades);
        ListIterator<Trade> it = trades.listIterator(trades.size());
        while (it.hasPrevious()) {
            newOrder.getSecurity().getOrderBook().restoreOrder(it.previous().getSell());
        }
    }

    public MatchResult execute(Order order, int openingPrice) {
        Order orderSnapshot = order.snapshot();
        if (order instanceof StopLimitOrder stopLimitOrder) {
            StopStatus creditStatus = creditHandler.initStopLimitOrderCredit(stopLimitOrder);
            if(creditStatus == StopStatus.NOT_ENOUGH){
                return MatchResult.notEnoughCredit();
            } else if (creditStatus == StopStatus.NOT_ACTIVATABLE) {
                order.getSecurity().getOrderBook().enqueue(order);
                return MatchResult.notActivatable();
            }
        }

        MatchResult result = match(order, openingPrice);
        if (result.outcome() == MatchingOutcome.NOT_ENOUGH_CREDIT)
            return result;

        if(!result.remainder().matchedMinimumExecutionQuantity(orderSnapshot.getQuantity()) && order.isNew()) {
            rollbackTrades(order, result.trades());
            return MatchResult.notEnoughExecutionQuantity();
        }

        if (result.remainder().getQuantity() > 0) {
            if (order.getSide() == Side.BUY) {
                if (!order.getBroker().hasEnoughCredit((long)order.getPrice() * order.getQuantity())) {
                    rollbackTrades(order, result.trades());
                    return MatchResult.notEnoughCredit();
                }
                order.getBroker().decreaseCreditBy((long)order.getPrice() * order.getQuantity());
            }
            order.getSecurity().getOrderBook().enqueue(result.remainder());
        }
        if (!result.trades().isEmpty()) {
            for (Trade trade : result.trades()) {
                trade.getBuy().getShareholder().incPosition(trade.getSecurity(), trade.getQuantity());
                trade.getSell().getShareholder().decPosition(trade.getSecurity(), trade.getQuantity());
            }
        }
        order.getSecurity().updateMarketPrice(result);
        return result;
    }

    public MatchResult execute(Order order) {
        return execute(order, 0);
    }

    public List<Trade> openMarket(Security security) {
        int openingPrice = security.getOpeningPrice();
        List<Trade> trades = new ArrayList<>();
        Order lastOrder = null;
        while (true) {
            Order order = security.getOrderBook().removeFirst(Side.BUY);
            if (order == null)
                break;
            if (order.equalIdandQuantity(lastOrder)) {
                security.getOrderBook().enqueue(order);
                break;
            }
            lastOrder = order.snapshot();
            order.getBroker().increaseCreditBy(order.getValue());
            MatchResult result = execute(order, openingPrice);
            if (!lastOrder.equalIdandQuantity(result.remainder()))
                trades.addAll(result.trades());
        }
        return trades;
    }

}
