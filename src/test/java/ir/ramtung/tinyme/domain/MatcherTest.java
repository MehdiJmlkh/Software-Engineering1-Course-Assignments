package ir.ramtung.tinyme.domain;

import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.Matcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.internal.matchers.Or;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(MockedJMSTestConfig.class)
@DirtiesContext
public class MatcherTest {
    private Security security;
    private Broker broker;
    private Shareholder shareholder;
    private OrderBook orderBook;
    private List<Order> orders;
    private List<Order> tradableOrders;
    @Autowired
    private Matcher matcher;

    @BeforeEach
    void setupOrderBook() {
        security = Security.builder().build();
        broker = Broker.builder().credit(100_000_000L).build();
        shareholder = Shareholder.builder().build();
        shareholder.incPosition(security, 100_000);
        orderBook = security.getOrderBook();
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
            new Order(10, security, Side.SELL, 65, 15820, broker, shareholder)
        );
        orders.forEach(order -> orderBook.enqueue(order));
    }

    void addTradableOrder() {
        orderBook = security.getOrderBook();
        tradableOrders = Arrays.asList(
                new Order(11, security, Side.BUY, 304, 16500, broker, shareholder),
                new Order(12, security, Side.BUY, 43, 16000, broker, shareholder),
                new Order(13, security, Side.BUY, 445, 15950, broker, shareholder),
                new Order(14, security, Side.BUY, 526, 15950, broker, shareholder),
                new Order(15, security, Side.BUY, 1000, 15800, broker, shareholder),
                new Order(16, security, Side.SELL, 350, 15700, broker, shareholder),
                new Order(17, security, Side.SELL, 285, 15710, broker, shareholder),
                new Order(18, security, Side.SELL, 800, 15710, broker, shareholder),
                new Order(19, security, Side.SELL, 340, 15720, broker, shareholder),
                new Order(20, security, Side.SELL, 65, 15720, broker, shareholder)
        );
        tradableOrders.forEach(order -> orderBook.enqueue(order));
    }

    @Test
    void new_sell_order_matches_completely_with_part_of_the_first_buy() {
        Order order = new Order(11, security, Side.SELL, 100, 15600, broker, shareholder);
        Trade trade = new Trade(security, 15700, 100, orders.get(0), order);
        MatchResult result = matcher.match(order);
        assertThat(result.remainder().getQuantity()).isEqualTo(0);
        assertThat(result.trades()).containsExactly(trade);
        assertThat(security.getOrderBook().getBuyQueue().getFirst().getQuantity()).isEqualTo(204);
    }

    @Test
    void new_sell_order_matches_partially_with_the_first_buy() {
        Order order = new Order(11, security, Side.SELL, 500, 15600, broker, shareholder);
        Trade trade = new Trade(security, 15700, 304, orders.get(0), order);
        MatchResult result = matcher.match(order);
        assertThat(result.remainder().getQuantity()).isEqualTo(196);
        assertThat(result.trades()).containsExactly(trade);
        assertThat(security.getOrderBook().getBuyQueue().getFirst().getOrderId()).isEqualTo(2);
    }

    @Test
    void new_sell_order_matches_partially_with_two_buys() {
        Order order = new Order(11, security, Side.SELL, 500, 15500, broker, shareholder);
        Trade trade1 = new Trade(security, 15700, 304, orders.get(0), order);
        Trade trade2 = new Trade(security, 15500, 43, orders.get(1), order.snapshotWithQuantity(196));
        MatchResult result = matcher.match(order);
        assertThat(result.remainder().getQuantity()).isEqualTo(153);
        assertThat(result.trades()).containsExactly(trade1, trade2);
        assertThat(security.getOrderBook().getBuyQueue().getFirst().getOrderId()).isEqualTo(3);
    }

    @Test
    void new_buy_order_matches_partially_with_the_entire_sell_queue() {
        Order order = new Order(11, security, Side.BUY, 2000, 15820, broker, shareholder);
        List<Trade> trades = new ArrayList<>();
        int totalTraded = 0;
        for (Order o : orders.subList(5, 10)) {
            trades.add(new Trade(security, o.getPrice(), o.getQuantity(),
                    order.snapshotWithQuantity(order.getQuantity() - totalTraded), o));
            totalTraded += o.getQuantity();
        }

        MatchResult result = matcher.match(order);
        assertThat(result.remainder().getQuantity()).isEqualTo(160);
        assertThat(result.trades()).isEqualTo(trades);
        assertThat(security.getOrderBook().getSellQueue()).isEmpty();
    }

    @Test
    void new_buy_order_does_not_match() {
        Order order = new Order(11, security, Side.BUY, 2000, 15500, broker, shareholder);
        MatchResult result = matcher.match(order);
        assertThat(result.remainder()).isEqualTo(order);
        assertThat(result.trades()).isEmpty();
    }

    @Test
    void iceberg_order_in_queue_matched_completely_after_three_rounds() {
        security = Security.builder().build();
        broker = Broker.builder().build();
        orderBook = security.getOrderBook();
        orders = Arrays.asList(
                new IcebergOrder(1, security, Side.BUY, 450, 15450, broker, shareholder, 200),
                new Order(2, security, Side.BUY, 70, 15450, broker, shareholder),
                new Order(3, security, Side.BUY, 1000, 15400, broker, shareholder)
        );
        orders.forEach(order -> orderBook.enqueue(order));
        Order order = new Order(4, security, Side.SELL, 600, 15450, broker, shareholder);
        List<Trade> trades = List.of(
                new Trade(security, 15450, 200, orders.get(0).snapshotWithQuantity(200), order.snapshotWithQuantity(600)),
                new Trade(security, 15450, 70, orders.get(1).snapshotWithQuantity(70), order.snapshotWithQuantity(400)),
                new Trade(security, 15450, 200, orders.get(0).snapshotWithQuantity(200), order.snapshotWithQuantity(330)),
                new Trade(security, 15450, 50, orders.get(0).snapshotWithQuantity(50), order.snapshotWithQuantity(130))
        );

        MatchResult result = matcher.match(order);

        assertThat(result.remainder().getQuantity()).isEqualTo(80);
        assertThat(result.trades()).isEqualTo(trades);
    }

    @Test
    void new_buy_order_does_not_match_minimum_execution_quantity() {
        Order order = new Order(11, security, Side.BUY, 2000, 15800, broker, shareholder, 400);
        MatchResult result = matcher.execute(order);

        assertThat(result.outcome()).isEqualTo(MatchingOutcome.NOT_ENOUGH_EXECUTION_QUANTITY);
    }

    @Test
    void new_sell_order_does_not_match_minimum_execution_quantity() {
        Order order = new Order(11, security, Side.SELL, 2000, 15700, broker, shareholder, 400);
        MatchResult result = matcher.execute(order);

        assertThat(result.outcome()).isEqualTo(MatchingOutcome.NOT_ENOUGH_EXECUTION_QUANTITY);
    }

    @Test
    void new_buy_order_match_minimum_execution_quantity() {
        Order order = new Order(11, security, Side.BUY, 2000, 15800, broker, shareholder, 350);
        MatchResult result = matcher.execute(order);

        assertThat(result.outcome()).isEqualTo(MatchingOutcome.EXECUTED);
    }

    @Test
    void new_sell_order_match_minimum_execution_quantity() {
        Order order = new Order(11, security, Side.SELL, 2000, 15700, broker, shareholder, 300);
        MatchResult result = matcher.execute(order);

        assertThat(result.outcome()).isEqualTo(MatchingOutcome.EXECUTED);
    }

    @Test
    void new_buy_stop_limit_order_failed_to_activate() {
        Order order = new Order(11, security, Side.SELL, 500, 15500, broker, shareholder);
        Order stopLimitOrder = new StopLimitOrder(12, security, Side.BUY, 2000, 15700, broker, shareholder, 15600);
        matcher.execute(order);
        MatchResult result = matcher.execute(stopLimitOrder);

        assertThat(security.getMarketPrice()).isEqualTo(15500);
        assertThat(result.outcome()).isEqualTo(MatchingOutcome.NOT_ACTIVATABLE);
    }

    @Test
    void new_sell_stop_limit_order_failed_to_activate() {
        Order order = new Order(11, security, Side.SELL, 500, 15500, broker, shareholder);
        Order stopLimitOrder = new StopLimitOrder(12, security, Side.SELL, 2000, 15700, broker, shareholder, 15300);
        matcher.execute(order);
        MatchResult result = matcher.execute(stopLimitOrder);

        assertThat(security.getMarketPrice()).isEqualTo(15500);
        assertThat(result.outcome()).isEqualTo(MatchingOutcome.NOT_ACTIVATABLE);
    }

    @Test
    void open_market_causes_no_trades() {
        assertThat(matcher.openMarket(security)).isEqualTo(new LinkedList<Trade>());
    }

    @Test
    void open_market_causes_many_trades_and_remainder_of_buy_order_queued() {
        addTradableOrder();

        List<Trade> trades = List.of(
                new Trade(security, 15800, 304, tradableOrders.get(0).snapshotWithQuantity(304), tradableOrders.get(5).snapshotWithQuantity(350)),
                new Trade(security, 15800, 43, tradableOrders.get(1).snapshotWithQuantity(43), tradableOrders.get(5).snapshotWithQuantity(46)),
                new Trade(security, 15800, 3, tradableOrders.get(2).snapshotWithQuantity(445), tradableOrders.get(5).snapshotWithQuantity(3)),
                new Trade(security, 15800, 285, tradableOrders.get(2).snapshotWithQuantity(442), tradableOrders.get(6).snapshotWithQuantity(285)),
                new Trade(security, 15800, 157, tradableOrders.get(2).snapshotWithQuantity(157), tradableOrders.get(7).snapshotWithQuantity(800)),
                new Trade(security, 15800, 526, tradableOrders.get(3).snapshotWithQuantity(526), tradableOrders.get(7).snapshotWithQuantity(643)),
                new Trade(security, 15800, 117, tradableOrders.get(4).snapshotWithQuantity(1000), tradableOrders.get(7).snapshotWithQuantity(117)),
                new Trade(security, 15800, 340, tradableOrders.get(4).snapshotWithQuantity(883), tradableOrders.get(8).snapshotWithQuantity(340)),
                new Trade(security, 15800, 65, tradableOrders.get(4).snapshotWithQuantity(543), tradableOrders.get(9).snapshotWithQuantity(65)),
                new Trade(security, 15800, 350, tradableOrders.get(4).snapshotWithQuantity(478), orders.get(5).snapshotWithQuantity(350))
        );
        assertThat(matcher.openMarket(security)).isEqualTo(trades);
        Order order = tradableOrders.get(4).snapshotWithQuantity(128);
        order.queue();
        assertThat(security.getOrderBook().getBuyQueue().getFirst()).isEqualTo(order);
    }

    @Test
    void open_market_causes_trades_and_remainder_of_sell_order_queued() {
        tradableOrders = Arrays.asList(
                new Order(13, security, Side.BUY, 445, 15950, broker, shareholder),
                new Order(19, security, Side.SELL, 1000, 15650, broker, shareholder)
        );
        tradableOrders.forEach(order -> orderBook.enqueue(order));

        List<Trade> trades = List.of(
                new Trade(security, 15650, 445, tradableOrders.get(0).snapshotWithQuantity(445), tradableOrders.get(1).snapshotWithQuantity(1000)),
                new Trade(security, 15650, 304, orders.get(0).snapshotWithQuantity(304), tradableOrders.get(1).snapshotWithQuantity(555))
        );
        assertThat(matcher.openMarket(security)).isEqualTo(trades);
        Order order = tradableOrders.get(1).snapshotWithQuantity(251);
        order.queue();
        assertThat(security.getOrderBook().getSellQueue().getFirst()).isEqualTo(order);
    }

}
