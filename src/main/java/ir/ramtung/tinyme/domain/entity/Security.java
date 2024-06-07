package ir.ramtung.tinyme.domain.entity;

import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.request.ChangeMatchingStateRq;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.domain.service.Matcher;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.request.MatchingState;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.*;

@Getter
@Setter
@Builder
public class Security {
    private String isin;
    @Builder.Default
    private int tickSize = 1;
    @Builder.Default
    private int lotSize = 1;
    @Builder.Default
    private OrderBook orderBook = new OrderBook();
    @Builder.Default
    private int marketPrice = 0;
    @Builder.Default
    private MatchingState matchingState = MatchingState.CONTINUOUS;

    public MatchResult newOrder(Order order, Matcher matcher) {
        if (matchingState == MatchingState.AUCTION) {
            if (order.getSide() == Side.BUY)
                order.getBroker().decreaseCreditBy(order.getValue());
            orderBook.enqueue(order);
            return MatchResult.queuedDuringAuctionState();
        }
        else
            return matcher.execute(order);
    }

    public void deleteOrder(Order order) {
        if (order.getSide() == Side.BUY)
            order.getBroker().increaseCreditBy(order.getValue());
        orderBook.removeByOrderId(order.getSide(), order.getOrderId());
    }

    public MatchResult updateOrder(EnterOrderRq updateOrderRq, Matcher matcher) {
        Order order = orderBook.findByOrderId(updateOrderRq.getSide(), updateOrderRq.getOrderId());

        if (order instanceof StopLimitOrder stopLimitOrder)
            stopLimitOrder.setRequestId(updateOrderRq.getRequestId());
        if (order.getMinimumExecutionQuantity() != updateOrderRq.getMinimumExecutionQuantity())
            return MatchResult.notEqualMinimumExecutionQuantity();

        if (updateOrderRq.getSide() == Side.SELL &&
                !order.getShareholder().hasEnoughPositionsOn(this,
                orderBook.totalSellQuantityByShareholder(order.getShareholder()) - order.getQuantity() + updateOrderRq.getQuantity()))
            return MatchResult.notEnoughPositions();

        boolean losesPriority = order.isQuantityIncreased(updateOrderRq.getQuantity())
                || updateOrderRq.getPrice() != order.getPrice()
                || ((order instanceof IcebergOrder icebergOrder) && (icebergOrder.getPeakSize() < updateOrderRq.getPeakSize()));

        if (updateOrderRq.getSide() == Side.BUY) {
            order.getBroker().increaseCreditBy(order.getValue());
        }
        Order originalOrder = order.snapshot();
        order.updateFromRequest(updateOrderRq);
        if (!losesPriority) {
            if (updateOrderRq.getSide() == Side.BUY) {
                order.getBroker().decreaseCreditBy(order.getValue());
            }
            return MatchResult.executed(null, List.of());
        }

        orderBook.removeByOrderId(updateOrderRq.getSide(), updateOrderRq.getOrderId());
        MatchResult matchResult = matcher.execute(order);
        if (matchResult.outcome() != MatchingOutcome.OK) {
            orderBook.enqueue(originalOrder);
            if (updateOrderRq.getSide() == Side.BUY) {
                originalOrder.getBroker().decreaseCreditBy(originalOrder.getValue());
            }
        }
        return matchResult;
    }

    public List<Trade> changeMatchingState(ChangeMatchingStateRq changeMatchingStateRq, Matcher matcher) {
        if (matchingState == MatchingState.AUCTION) {
            matchingState = changeMatchingStateRq.getTargetState();
            return matcher.openMarket(this);
        }
        matchingState = changeMatchingStateRq.getTargetState();
        return new ArrayList<>();
    }

    public int getOpeningPrice() {
        HashSet<Integer> allPrice = orderBook.allPrices();
        allPrice.add(marketPrice);
        int openingPrice = allPrice.stream()
                .map(price -> Arrays.asList(tradableQuantity(price),
                                            Math.abs(marketPrice - price),
                                            price))
                .sorted(Comparator.comparing(
                        (List<Integer> tuple) -> -tuple.get(0))
                        .thenComparing(tuple -> tuple.get(1))
                        .thenComparing(tuple -> tuple.get(2)))
                .toList()
                .get(0).get(2);
        return tradableQuantity(openingPrice) == 0 ? 0 : openingPrice;
    }

    public int tradableQuantity(int openingPrice) {
        return Math.min(orderBook.totalTradableQuantity(openingPrice, Side.BUY), orderBook.totalTradableQuantity(openingPrice, Side.SELL));
    }

    public int tradableQuantity() {
        return tradableQuantity(getOpeningPrice());
    }

    public StopLimitOrder triggerOrder() {
        var stopLimitOrder = getOrderBook().activateFirst(Side.BUY, getMarketPrice());
        if (stopLimitOrder == null)
            stopLimitOrder = getOrderBook().activateFirst(Side.SELL, getMarketPrice());

        if (stopLimitOrder == null)
            return null;
        return stopLimitOrder;
    }

    public void updateMarketPrice(MatchResult matchResult) {
        if (!matchResult.trades().isEmpty()) {
            marketPrice = matchResult.trades().getLast().getPrice();
        }
    }
}
