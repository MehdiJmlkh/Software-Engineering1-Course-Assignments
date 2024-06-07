package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.entity.MatchingOutcome;
import ir.ramtung.tinyme.domain.service.control.MatchingControlList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

@Service
public class Matcher {
    @Autowired
    private MatchingControlList controls;

    public MatchResult match(Order newOrder,  int openingPrice) {
        OrderBook orderBook = newOrder.getSecurity().getOrderBook();
        LinkedList<Trade> trades = new LinkedList<>();

        while (orderBook.hasOrderOfType(newOrder.getSide().opposite()) && newOrder.getQuantity() > 0) {
            Order matchingOrder = orderBook.matchWithFirst(newOrder);
            if (matchingOrder == null)
                break;

            int price = openingPrice == 0 ? matchingOrder.getPrice() : openingPrice;
            Trade trade = new Trade(newOrder.getSecurity(), price, Math.min(newOrder.getQuantity(), matchingOrder.getQuantity()), newOrder, matchingOrder);

            MatchingOutcome outcome = controls.canTrade(newOrder, trade);
            if (outcome != MatchingOutcome.OK) {
                controls.rollbackTrades(newOrder, trades);
                rollbackTrades(newOrder, trades);
                return new MatchResult(outcome, newOrder);
            }

            trades.add(trade);
            controls.tradeAccepted(newOrder, trade);

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
        ListIterator<Trade> it = trades.listIterator(trades.size());
        while (it.hasPrevious()) {
            if (newOrder.getSide() == Side.BUY)
                newOrder.getSecurity().getOrderBook().restoreOrder(it.previous().getSell());
            else
                newOrder.getSecurity().getOrderBook().restoreOrder(it.previous().getBuy());
        }
    }

    public MatchResult execute(Order order, int openingPrice) {
        MatchingOutcome outcome = controls.canStartMatching(order);
        if (outcome != MatchingOutcome.OK)
            return new MatchResult(outcome, order);

        controls.matchingStarted(order);

        outcome = controls.canContinueMatching(order);
        if (outcome != MatchingOutcome.OK) {
            return new MatchResult(outcome, order);
        }

        MatchResult result = match(order, openingPrice);
        if (result.outcome() != MatchingOutcome.OK)
            return result;

        outcome = controls.canAcceptMatching(order, result);
        if (outcome != MatchingOutcome.OK) {
            controls.rollbackTrades(order, result.trades());
            rollbackTrades(order, result.trades());
            return new MatchResult(outcome, order);
        }

        if (result.remainder().getQuantity() > 0) {
            order.getSecurity().getOrderBook().enqueue(result.remainder());
        }

        controls.matchingAccepted(order, result);

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

            controls.marketOpenned(order);

            MatchResult result = execute(order, openingPrice);
            if (!lastOrder.equalIdandQuantity(result.remainder()))
                trades.addAll(result.trades());
        }
        return trades;
    }

}
