package ir.ramtung.tinyme.domain.entity;

import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class StopLimitOrder extends Order{
    protected int stopPrice;

    public StopLimitOrder(long orderId, Security security, Side side, int quantity, int price, Broker broker, Shareholder shareholder, LocalDateTime entryTime, OrderStatus status, int stopPrice) {
        super(orderId, security, side, quantity, price, broker, shareholder, entryTime,  status);
        this.stopPrice = stopPrice;
    }

    public StopLimitOrder(long orderId, Security security, Side side, int quantity, int price, Broker broker, Shareholder shareholder, LocalDateTime entryTime, int stopPrice) {
        this(orderId, security, side, quantity, price, broker, shareholder, entryTime,  OrderStatus.NEW, stopPrice);
    }

    public StopLimitOrder(long orderId, Security security, Side side, int quantity, int price, Broker broker, Shareholder shareholder, int stopPrice) {
        this(orderId, security, side, quantity, price, broker, shareholder, LocalDateTime.now(),  OrderStatus.NEW, stopPrice);
    }

    public boolean queuesBefore(StopLimitOrder other) {
        if (other.getSide() == Side.BUY)
            return stopPrice < other.getStopPrice();
        else
            return stopPrice > other.getStopPrice();
    }

    public boolean isActivatable(int marketPrice) {
        if (side == Side.BUY)
            return stopPrice <= marketPrice;
        else
            return stopPrice >= marketPrice;
    }

    @Override
    public void updateFromRequest(EnterOrderRq updateOrderRq) {
        super.updateFromRequest(updateOrderRq);
        stopPrice = updateOrderRq.getStopPrice();
    }

    public Order activate() {
        return new Order(orderId, security, side, quantity, price, broker, shareholder, entryTime);
    }
}
