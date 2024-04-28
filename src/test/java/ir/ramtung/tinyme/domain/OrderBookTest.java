package ir.ramtung.tinyme.domain;

import ir.ramtung.tinyme.domain.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderBookTest {
    private Security security;
    private List<Order> orders;
    @BeforeEach
    void setupOrderBook() {
        security = Security.builder().build();
        Broker broker = Broker.builder().build();
        Shareholder shareholder = Shareholder.builder().build();
        shareholder.incPosition(security, 100_000);
        orders = Arrays.asList(
                new Order(1, security, Side.BUY, 304, 15700, broker, shareholder),
                new Order(2, security, Side.BUY, 43, 15500, broker, shareholder),
                new Order(3, security, Side.BUY, 445, 15450, broker, shareholder),
                new Order(4, security, Side.BUY, 526, 15450, broker, shareholder),
                new Order(5, security, Side.BUY, 1000, 15400, broker, shareholder),
                new Order(6, security, Side.SELL, 350, 15800, broker, shareholder),
                new Order(7, security, Side.SELL, 285, 15810, broker, shareholder),
                new Order(8, security, Side.SELL, 800, 15810, broker, shareholder),
                new Order(9, security, Side.SELL, 340, 15820, broker, shareholder),
                new Order(10, security, Side.SELL, 65, 15820, broker, shareholder),
                new StopLimitOrder(11, security, Side.BUY, 340, 15750, broker, shareholder, 15700),
                new StopLimitOrder(12, security, Side.BUY, 200, 15850, broker, shareholder, 15800),
                new StopLimitOrder(13, security, Side.BUY, 500, 15850, broker, shareholder, 15850),
                new StopLimitOrder(14, security, Side.SELL, 320, 15500, broker, shareholder, 15600),
                new StopLimitOrder(15, security, Side.SELL, 85, 15350, broker, shareholder, 15500),
                new StopLimitOrder(16, security, Side.SELL, 85, 15300, broker, shareholder, 15400)
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));
    }

    @Test
    void finds_the_first_order_by_id() {
        assertThat(security.getOrderBook().findByOrderId(Side.BUY, 1))
                .isEqualTo(orders.get(0));
    }

    @Test
    void finds_some_order_by_id_in_the_stop_queue() {
        assertThat(security.getOrderBook().findByOrderId(Side.BUY, 11))
                .isEqualTo(orders.get(10));
    }

    @Test
    void fails_to_find_the_first_order_by_id_in_the_wrong_queue() {
        assertThat(security.getOrderBook().findByOrderId(Side.SELL, 1)).isNull();
    }

    @Test
    void finds_some_order_in_the_middle_by_id() {
        assertThat(security.getOrderBook().findByOrderId(Side.BUY, 3))
                .isEqualTo(orders.get(2));
    }

    @Test
    void finds_the_last_order_by_id() {
        assertThat(security.getOrderBook().findByOrderId(Side.SELL, 10))
                .isEqualTo(orders.get(9));
    }

    @Test
    void finds_the_last_order_by_id_in_stop_queue() {
        assertThat(security.getOrderBook().findByOrderId(Side.SELL, 16))
                .isEqualTo(orders.get(15));
    }

    @Test
    void removes_the_first_order_by_id() {
        OrderBook orderBook = security.getOrderBook();
        orderBook.removeByOrderId(Side.BUY, 1);
        assertThat(orderBook.getBuyQueue()).isEqualTo(orders.subList(1, 5));
    }

    @Test
    void removes_the_first_stop_limit_order_by_id() {
        OrderBook orderBook = security.getOrderBook();
        orderBook.removeByOrderId(Side.BUY, 11);
        assertThat(orderBook.getStopBuyQueue()).isEqualTo(orders.subList(11, 13));
    }

    @Test
    void removes_the_last_stop_limit_order_by_id() {
        OrderBook orderBook = security.getOrderBook();
        orderBook.removeByOrderId(Side.SELL, 16);
        assertThat(orderBook.getStopSellQueue()).isEqualTo(orders.subList(13, 15));
    }

    @Test
    void fails_to_remove_the_first_order_by_id_in_the_wrong_queue() {
        OrderBook orderBook = security.getOrderBook();
        orderBook.removeByOrderId(Side.SELL, 1);
        assertThat(orderBook.getBuyQueue()).isEqualTo(orders.subList(0, 5));
    }

    @Test
    void removes_the_last_order_by_id() {
        OrderBook orderBook = security.getOrderBook();
        orderBook.removeByOrderId(Side.SELL, 10);
        assertThat(orderBook.getSellQueue()).isEqualTo(orders.subList(5, 9));
    }
}