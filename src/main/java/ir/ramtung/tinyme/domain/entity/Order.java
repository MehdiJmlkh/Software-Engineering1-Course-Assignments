package ir.ramtung.tinyme.domain.entity;

import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import lombok.*;
import org.springframework.http.server.DelegatingServerHttpResponse;

import java.time.LocalDateTime;

import static java.lang.Double.POSITIVE_INFINITY;

@Builder
@EqualsAndHashCode
@ToString
@Getter
public class Order {
    protected long orderId;
    protected Security security;
    protected Side side;
    protected int initialQuantity;
    protected int quantity;
    protected int price;
    protected Broker broker;
    protected Shareholder shareholder;
    @Builder.Default
    protected LocalDateTime entryTime = LocalDateTime.now();
    @Setter
    @Builder.Default
    protected OrderStatus status = OrderStatus.NEW;
    protected int minimumExecutionQuantity = 0;

    public Order(long orderId, Security security, Side side,int initialQuantity, int quantity, int price, Broker broker, Shareholder shareholder, LocalDateTime entryTime, OrderStatus status, int minimumExecutionQuantity) {
        this.orderId = orderId;
        this.security = security;
        this.side = side;
        this.initialQuantity = initialQuantity;
        this.quantity = quantity;
        this.price = price;
        this.entryTime = entryTime;
        this.broker = broker;
        this.shareholder = shareholder;
        this.status = status;
        this.minimumExecutionQuantity = minimumExecutionQuantity;
    }

    public Order(long orderId, Security security, Side side, int quantity, int price, Broker broker, Shareholder shareholder, LocalDateTime entryTime, OrderStatus status, int minimumExecutionQuantity) {
        this.orderId = orderId;
        this.security = security;
        this.side = side;
        this.quantity = quantity;
        this.initialQuantity = quantity;
        this.price = price;
        this.entryTime = entryTime;
        this.broker = broker;
        this.shareholder = shareholder;
        this.status = status;
        this.minimumExecutionQuantity = minimumExecutionQuantity;
    }

    public Order(long orderId, Security security, Side side, int quantity, int price, Broker broker, Shareholder shareholder, LocalDateTime entryTime, OrderStatus status) {
        this(orderId, security, side, quantity, price, broker, shareholder, entryTime,  status, 0);
    }

    public Order(long orderId, Security security, Side side, int quantity, int price, Broker broker, Shareholder shareholder, LocalDateTime entryTime, int minimumExecutionQuantity) {
        this(orderId, security, side, quantity, price, broker, shareholder, entryTime,  OrderStatus.NEW, minimumExecutionQuantity);
    }

    public Order(long orderId, Security security, Side side, int quantity, int price, Broker broker, Shareholder shareholder, LocalDateTime entryTime) {
        this(orderId, security, side, quantity, price, broker, shareholder, entryTime,  OrderStatus.NEW, 0);
    }

    public Order(long orderId, Security security, Side side, int quantity, int price, Broker broker, Shareholder shareholder, int minimumExecutionQuantity) {
        this(orderId, security, side, quantity, price, broker, shareholder, LocalDateTime.now(),  OrderStatus.NEW, minimumExecutionQuantity);
    }

    public Order(long orderId, Security security, Side side, int quantity, int price, Broker broker, Shareholder shareholder) {
        this(orderId, security, side, quantity, price, broker, shareholder, LocalDateTime.now());
    }

    public Order snapshot() {
        return new Order(orderId, security, side, initialQuantity, quantity, price, broker, shareholder, entryTime, OrderStatus.SNAPSHOT, minimumExecutionQuantity);
    }

    public Order snapshotWithQuantity(int newQuantity) {
        return new Order(orderId, security, side, initialQuantity, newQuantity, price, broker, shareholder, entryTime, OrderStatus.SNAPSHOT, minimumExecutionQuantity);
    }

    public boolean matches(Order other) {
        if (side == Side.BUY)
            return price >= other.price;
        else
            return price <= other.price;
    }

    public boolean matches(int tradePrice) {
        if (side == Side.BUY)
            return price >= tradePrice;
        else
            return price <= tradePrice;
    }

    public void decreaseQuantity(int amount) {
        if (amount > quantity)
            throw new IllegalArgumentException();
        quantity -= amount;
    }

    public void makeQuantityZero() {
        quantity = 0;
    }

    public boolean queuesBefore(Order order) {
        if (order.getSide() == Side.BUY)
            return price > order.getPrice();
        else
            return price < order.getPrice();

    }

    public void queue() {
        status = OrderStatus.QUEUED;
    }

    public boolean isQuantityIncreased(int newQuantity) {
        return newQuantity > quantity;
    }

    public void updateFromRequest(EnterOrderRq updateOrderRq) {
        quantity = updateOrderRq.getQuantity();
        price = updateOrderRq.getPrice();
    }

    public boolean minimumExecutionQuantitySatisfied() {
        return (initialQuantity - quantity) >= minimumExecutionQuantity;
    }

    public long getValue() {
        return (long)price * quantity;
    }

    public int getTotalQuantity() { return quantity; }

    public boolean isNew() {
        return status == OrderStatus.NEW;
    }

    public boolean equalIdandQuantity(Order other) {
        if (other == null)
            return false;
        return orderId == other.getOrderId() && getTotalQuantity() == other.getTotalQuantity();
    }

    public boolean losesPriority(EnterOrderRq updateOrderRq) {
        return isQuantityIncreased(updateOrderRq.getQuantity())
                || updateOrderRq.getPrice() != price;
    }
}
