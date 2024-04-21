package ir.ramtung.tinyme.domain.entity;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class StopLimitOrder extends Order{
    protected int stopPrice;

    public StopLimitOrder(long orderId, Security security, Side side, int quantity, int price, Broker broker, Shareholder shareholder, LocalDateTime entryTime, OrderStatus status, int stopPrice) {
        super(orderId, security, side, quantity, price, broker, shareholder, entryTime,  status);
        this.stopPrice = stopPrice;
    }

    public boolean queuesBefore(StopLimitOrder other) {
        if (other.getSide() == Side.BUY)
            return stopPrice < other.getStopPrice();
        else
            return stopPrice > other.getStopPrice();
    }
}
