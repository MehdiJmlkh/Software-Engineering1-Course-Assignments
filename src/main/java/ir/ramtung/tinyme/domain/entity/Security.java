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
import java.util.stream.Collectors;

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

    public void deleteOrder(DeleteOrderRq deleteOrderRq) {
        Order order = orderBook.findByOrderId(deleteOrderRq.getSide(), deleteOrderRq.getOrderId());
        if (order.getSide() == Side.BUY)
            order.getBroker().increaseCreditBy(order.getValue());
        orderBook.removeByOrderId(order.getSide(), order.getOrderId());
    }

    public MatchResult updateOrder(EnterOrderRq updateOrderRq, Matcher matcher) {
        Order order = orderBook.findByOrderId(updateOrderRq.getSide(), updateOrderRq.getOrderId());

        if (order instanceof StopLimitOrder stopLimitOrder)
            stopLimitOrder.setRequestId(updateOrderRq.getRequestId());

        MatchingOutcome outcome = canStartUpdating(updateOrderRq, order);
        if (outcome != MatchingOutcome.OK)
            return new MatchResult(outcome, null);

        if (!order.losesPriority(updateOrderRq)) {
            order.updateFromRequest(updateOrderRq);
            return MatchResult.executed(null, List.of());
        }

        updatingStarted(updateOrderRq, order);

        Order originalOrder = order.snapshot();
        order.updateFromRequest(updateOrderRq);

        orderBook.removeByOrderId(updateOrderRq.getSide(), updateOrderRq.getOrderId());
        MatchResult matchResult = matcher.execute(order);
        if (matchResult.outcome() != MatchingOutcome.OK) {
            orderBook.enqueue(originalOrder);
            updatingFailed(updateOrderRq, originalOrder);
        }
        return matchResult;
    }

    private MatchingOutcome canStartUpdating(EnterOrderRq updateOrderRq, Order order) {
        if ( updateOrderRq.getSide() == Side.SELL &&
                !order.getShareholder().hasEnoughPositionsOn(this,
                        orderBook.totalSellQuantityByShareholder(order.getShareholder()) - order.getQuantity() + updateOrderRq.getQuantity()))
            return MatchingOutcome.NOT_ENOUGH_POSITIONS;

        return MatchingOutcome.OK;
    }

    private void updatingStarted(EnterOrderRq updateOrderRq, Order order) {
        if (updateOrderRq.getSide() == Side.BUY) {
            order.getBroker().increaseCreditBy(order.getValue());
        }
    }

    private static void updatingFailed(EnterOrderRq updateOrderRq, Order originalOrder) {
        if (updateOrderRq.getSide() == Side.BUY) {
            originalOrder.getBroker().decreaseCreditBy(originalOrder.getValue());
        }
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
        HashSet<Integer> allPrices = orderBook.allPrices();
        allPrices.add(marketPrice);
        int openingPrice = allPrices.stream()
                .map(price -> new HashMap<String, Integer>() {{
                    put("tradableQuantity", tradableQuantity(price));
                    put("priceDiff", Math.abs(marketPrice - price));
                    put("price", price);
                }})
                .sorted(Comparator.comparing(
                        (Map<String, Integer> priceInfo) -> -priceInfo.get("tradableQuantity"))
                        .thenComparing(priceInfo -> priceInfo.get("priceDiff"))
                        .thenComparing(priceInfo -> priceInfo.get("price")))
                .map(priceInfo -> priceInfo.get("price"))
                .findFirst()
                .orElse(0);

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

        return stopLimitOrder;
    }

    public void updateMarketPrice(MatchResult matchResult) {
        if (!matchResult.trades().isEmpty()) {
            marketPrice = matchResult.trades().getLast().getPrice();
        }
    }
}
