package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

@Service
public class Matcher {
    public MatchResult match(Order newOrder, int openingPrice) {
        OrderBook orderBook = newOrder.getSecurity().getOrderBook();
        LinkedList<Trade> trades = new LinkedList<>();

        while (orderBook.hasOrderOfType(newOrder.getSide().opposite()) && newOrder.getQuantity() > 0) {
            Order matchingOrder = orderBook.matchWithFirst(newOrder);
            if (matchingOrder == null)
                break;
            int price = openingPrice == 0 ? matchingOrder.getPrice() : openingPrice;
            Trade trade = new Trade(newOrder.getSecurity(), price, Math.min(newOrder.getQuantity(), matchingOrder.getQuantity()), newOrder, matchingOrder);
            if (newOrder.getSide() == Side.BUY) {
                if (trade.buyerHasEnoughCredit())
                    trade.decreaseBuyersCredit();
                else {
                    rollbackTrades(newOrder, trades);
                    return MatchResult.notEnoughCredit();
                }
            }
            trade.increaseSellersCredit();
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
        return MatchResult.executed(newOrder, trades);
    }

    public MatchResult match(Order newOrder) {
        return match(newOrder, 0);
    }

    private void rollbackTrades(Order newOrder, LinkedList<Trade> trades) {
        if (newOrder.getSide() == Side.BUY) {
            newOrder.getBroker().increaseCreditBy(trades.stream().mapToLong(Trade::getTradedValue).sum());
            trades.forEach(trade -> trade.getSell().getBroker().decreaseCreditBy(trade.getTradedValue()));
        }
        else
            newOrder.getBroker().decreaseCreditBy(trades.stream().mapToLong(Trade::getTradedValue).sum());

        ListIterator<Trade> it = trades.listIterator(trades.size());
        while (it.hasPrevious()) {
            newOrder.getSecurity().getOrderBook().restoreOrder(it.previous().getSell());
        }
    }

    public MatchResult execute(Order order, int openingPrice) {
        Order orderSnapshot = order.snapshot();
        if (order instanceof StopLimitOrder stopLimitOrder) {
            if (order.getSide() == Side.BUY && !order.getBroker().hasEnoughCredit(order.getValue()))
                return MatchResult.notEnoughCredit();
            if (!stopLimitOrder.isActivatable(order.getSecurity().getMarketPrice())) {
                if (order.getSide() == Side.BUY)
                    order.getBroker().decreaseCreditBy(order.getValue());
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
            MatchResult result = execute(order, security.getOpeningPrice());
            if (!result.remainder().equalIdandQuantity(lastOrder))
                trades.addAll(result.trades());
        }
        return trades;
    }

}
