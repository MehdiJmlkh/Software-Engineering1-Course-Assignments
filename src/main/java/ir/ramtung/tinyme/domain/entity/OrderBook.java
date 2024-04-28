package ir.ramtung.tinyme.domain.entity;

import lombok.Getter;

import java.util.*;

@Getter
public class OrderBook {
    private final LinkedList<Order> buyQueue;
    private final LinkedList<Order> sellQueue;
    private final LinkedList<Order> stopBuyQueue;
    private final LinkedList<Order> stopSellQueue;

    public OrderBook() {
        buyQueue = new LinkedList<>();
        sellQueue = new LinkedList<>();
        stopBuyQueue = new LinkedList<>();
        stopSellQueue = new LinkedList<>();
    }

    public void enqueue(Order order) {
        List<Order> queue = getQueue(order.getSide(), order instanceof StopLimitOrder);
        ListIterator<Order> it = queue.listIterator();
        while (it.hasNext()) {
            if (order.queuesBefore(it.next())) {
                it.previous();
                break;
            }
        }
        order.queue();
        it.add(order);
    }

    private LinkedList<Order> getQueue(Side side) {
        return side == Side.BUY ? buyQueue : sellQueue;
    }

    private LinkedList<Order> getQueue(Side side, boolean stop) {
        return side == Side.BUY ? stop ? stopBuyQueue : buyQueue :
                                  stop ? stopSellQueue : sellQueue;
    }

    private ListIterator<Order> exploreQueue(LinkedList<Order> queue, long orderId) {
        var it = queue.listIterator();
        while (it.hasNext()) {
            if (it.next().getOrderId() == orderId) {
                return it;
            }
        }
        return null;
    }

    private ListIterator<Order> findOrderIteratorById(Side side, long orderId) {
        var it = exploreQueue(getQueue(side), orderId);
        if (it != null) {
            return it;
        }
        return exploreQueue(getQueue(side, true), orderId);
    }

    public Order findByOrderId(Side side, long orderId) {
        var it = findOrderIteratorById(side, orderId);
        if (it != null){
            it.previous();
            return it.next();
        }
        return null;
    }

    public boolean removeByOrderId(Side side, long orderId) {
        var it = findOrderIteratorById(side, orderId);
        if (it != null){
            it.remove();
            return true;
        }
        return false;
    }

    public Order matchWithFirst(Order newOrder) {
        var queue = getQueue(newOrder.getSide().opposite());
        if (newOrder.matches(queue.getFirst()))
            return queue.getFirst();
        else
            return null;
    }

    public StopLimitOrder activateFirst(Side side, int marketPrice) {
        var queue = getQueue(side, true);
        StopLimitOrder order;
        if (!queue.isEmpty()) {
            order = (StopLimitOrder) queue.getFirst();
            if (order.isActivatable(marketPrice)) {
                queue.removeFirst();
                return order;
            }
        }
        return null;
    }

    public void putBack(Order order) {
        LinkedList<Order> queue = getQueue(order.getSide(), order instanceof StopLimitOrder);
        order.queue();
        queue.addFirst(order);
    }

    public void restoreOrder(Order order) {
        removeByOrderId(order.getSide(), order.getOrderId());
        putBack(order);
    }

    public boolean hasOrderOfType(Side side) {
        return !getQueue(side).isEmpty();
    }

    public void removeFirst(Side side) {
        getQueue(side).removeFirst();
    }

    public int totalSellQuantityByShareholder(Shareholder shareholder) {
        return sellQueue.stream()
                .filter(order -> order.getShareholder().equals(shareholder))
                .mapToInt(Order::getTotalQuantity)
                .sum();
    }
}
