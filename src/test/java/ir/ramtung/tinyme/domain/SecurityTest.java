package ir.ramtung.tinyme.domain;

import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.domain.service.Matcher;
import ir.ramtung.tinyme.messaging.request.MatchingState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Import(MockedJMSTestConfig.class)
class SecurityTest {
    private Security security;
    private Broker broker;
    private Shareholder shareholder;
    private List<Order> orders;
    @Autowired
    Matcher matcher;
    @BeforeEach
    void setupOrderBook() {
        security = Security.builder().build();
        broker = Broker.builder().brokerId(0).credit(1_000_000L).build();
        shareholder = Shareholder.builder().shareholderId(0).build();
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
                new Order(10, security, Side.SELL, 65, 15820, broker, shareholder)
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));
    }

    private void setupOrderBookWithIcebergOrder() {
        security = Security.builder().build();
        broker = Broker.builder().build();
        orders = Arrays.asList(
                new Order(1, security, Side.BUY, 304, 15700, broker, shareholder),
                new Order(2, security, Side.BUY, 43, 15500, broker, shareholder),
                new IcebergOrder(3, security, Side.BUY, 445, 15450, broker, shareholder, 100),
                new Order(4, security, Side.BUY, 526, 15450, broker, shareholder),
                new Order(5, security, Side.BUY, 1000, 15400, broker, shareholder)
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));
    }

    @Test
    void reducing_quantity_does_not_change_priority() {
        EnterOrderRq updateOrderRq = EnterOrderRq.createUpdateOrderRq(1, security.getIsin(), 3, LocalDateTime.now(), Side.BUY, 440, 15450, 0, 0, 0);
        assertThatNoException().isThrownBy(() -> security.updateOrder(updateOrderRq, matcher));
        assertThat(security.getOrderBook().getBuyQueue().get(2).getQuantity()).isEqualTo(440);
        assertThat(security.getOrderBook().getBuyQueue().get(2).getOrderId()).isEqualTo(3);
    }

    @Test
    void increasing_quantity_changes_priority() {
        EnterOrderRq updateOrderRq = EnterOrderRq.createUpdateOrderRq(1, security.getIsin(), 3, LocalDateTime.now(), Side.BUY, 450, 15450, 0, 0, 0);
        assertThatNoException().isThrownBy(() -> security.updateOrder(updateOrderRq, matcher));
        assertThat(security.getOrderBook().getBuyQueue().get(3).getQuantity()).isEqualTo(450);
        assertThat(security.getOrderBook().getBuyQueue().get(3).getOrderId()).isEqualTo(3);
    }

    @Test
    void changing_price_changes_priority() {
        EnterOrderRq updateOrderRq = EnterOrderRq.createUpdateOrderRq(1, security.getIsin(), 1, LocalDateTime.now(), Side.BUY, 300, 15450, 0, 0, 0);
        assertThatNoException().isThrownBy(() -> security.updateOrder(updateOrderRq, matcher));
        assertThat(security.getOrderBook().getBuyQueue().get(3).getQuantity()).isEqualTo(300);
        assertThat(security.getOrderBook().getBuyQueue().get(3).getPrice()).isEqualTo(15450);
        assertThat(security.getOrderBook().getBuyQueue().get(3).getOrderId()).isEqualTo(1);
        assertThat(security.getOrderBook().getBuyQueue().get(0).getOrderId()).isEqualTo(2);
    }
    @Test
    void changing_price_causes_trades_to_happen() {
        EnterOrderRq updateOrderRq = EnterOrderRq.createUpdateOrderRq(1, security.getIsin(), 6, LocalDateTime.now(), Side.SELL, 350, 15700, 0, 0, 0);
        assertThatNoException().isThrownBy(() ->
                assertThat(security.updateOrder(updateOrderRq, matcher).trades()).isNotEmpty()
        );
    }


    @Test
    void delete_order_works() {
        DeleteOrderRq deleteOrderRq = new DeleteOrderRq(1, security.getIsin(), Side.SELL, 6);
        assertThatNoException().isThrownBy(() -> security.deleteOrder(deleteOrderRq));
        assertThat(security.getOrderBook().getBuyQueue()).isEqualTo(orders.subList(0, 5));
        assertThat(security.getOrderBook().getSellQueue()).isEqualTo(orders.subList(6, 10));
    }

    @Test
    void increasing_iceberg_peak_size_changes_priority() {
        setupOrderBookWithIcebergOrder();
        EnterOrderRq updateOrderRq = EnterOrderRq.createUpdateOrderRq(1, security.getIsin(), 3, LocalDateTime.now(), Side.BUY, 445, 15450, 0, 0, 150);
        assertThatNoException().isThrownBy(() -> security.updateOrder(updateOrderRq, matcher));
        assertThat(security.getOrderBook().getBuyQueue().get(3).getQuantity()).isEqualTo(150);
        assertThat(security.getOrderBook().getBuyQueue().get(3).getOrderId()).isEqualTo(3);
    }

    @Test
    void decreasing_iceberg_quantity_to_amount_larger_than_peak_size_does_not_changes_priority() {
        setupOrderBookWithIcebergOrder();
        EnterOrderRq updateOrderRq = EnterOrderRq.createUpdateOrderRq(1, security.getIsin(), 3, LocalDateTime.now(), Side.BUY, 300, 15450, 0, 0, 100);
        assertThatNoException().isThrownBy(() -> security.updateOrder(updateOrderRq, matcher));
        assertThat(security.getOrderBook().getBuyQueue().get(2).getOrderId()).isEqualTo(3);
    }

    @Test
    void validate_market_price(){
        Order order = new Order(50, security, Side.SELL, 304, 15600, broker, shareholder);
        matcher.execute(order);
        assertThat(security.getOrderBook().getBuyQueue().get(0).getOrderId()).isEqualTo(2);
        assertThat(security.getMarketPrice()).isEqualTo(15700);
    }

    @Test
    void update_stop_limit_order_will_change_request_id() {
        security.getOrderBook().enqueue(new StopLimitOrder(1, 11, security, Side.BUY, 100, 15800, broker, shareholder, 15700));
        security.updateOrder(EnterOrderRq.createUpdateOrderRq(2, security.getIsin(),11, LocalDateTime.now(), Side.BUY, 100, 15700, broker.getBrokerId(), shareholder.getShareholderId(), 0, 0, 15700), matcher);
        assertThat(((StopLimitOrder)security.getOrderBook().getStopBuyQueue().getFirst()).getRequestId()).isEqualTo(2);
    }

    @Test
    void opening_price_is_equal_to_last_new_sell_order_price(){
        security.setMatchingState(MatchingState.AUCTION);
        security.getOrderBook().enqueue(new Order(1, security, Side.SELL, 2000, 15400, broker, shareholder));
        assertThat(security.getOpeningPrice()).isEqualTo(15400);
    }

    @Test
    void opening_price_is_equal_to_market_price(){
        security.setMatchingState(MatchingState.AUCTION);
        security.setMarketPrice(15650);
        security.getOrderBook().enqueue(new Order(1, security, Side.SELL, 300, 15600, broker, shareholder));
        assertThat(security.getOpeningPrice()).isEqualTo(15650);
    }

    @Test
    void opening_price_is_equal_to_an_sell_order_price_and_greater_than_market_price() {
        security.setMatchingState(MatchingState.AUCTION);
        security.setMarketPrice(15650);
        security.getOrderBook().enqueue(new Order(1, security, Side.BUY, 1500, 15815, broker, shareholder));
        assertThat(security.getOpeningPrice()).isEqualTo(15810);
    }

    @Test
    void opening_price_is_equal_to_an_buy_order_price_and_less_than_market_price() {
        security.setMatchingState(MatchingState.AUCTION);
        security.setMarketPrice(15820);
        security.getOrderBook().enqueue(new Order(1, security, Side.BUY, 1500, 15815, broker, shareholder));
        assertThat(security.getOpeningPrice()).isEqualTo(15815);
    }

    @Test
    void opening_price_is_equal_to_zero_when_no_order_can_match(){
        security.setMarketPrice(15650);
        assertThat(security.getOpeningPrice()).isEqualTo(0);
    }

    @Test
    void tradable_quantity_is_equal_to_last_new_sell_order_quantity(){
        security.setMatchingState(MatchingState.AUCTION);
        security.getOrderBook().enqueue(new Order(1, security, Side.SELL, 2000, 15400, broker, shareholder));
        assertThat(security.tradableQuantity()).isEqualTo(2000);
    }

    @Test
    void tradable_quantity_is_zero_when_no_order_can_match(){
        security.setMarketPrice(15650);
        assertThat(security.tradableQuantity()).isEqualTo(0);
    }

    @Test
    void tradable_quantity_is_equal_to_sum_of_some_sell_order(){
        security.setMatchingState(MatchingState.AUCTION);
        security.setMarketPrice(15650);
        security.getOrderBook().enqueue(new Order(1, security, Side.BUY, 1500, 15815, broker, shareholder));
        assertThat(security.tradableQuantity()).isEqualTo(1435);
    }

    @Test
    void consider_total_quantity_of_iceberg_order_in_calculating_tradable_quantity() {
        security.setMatchingState(MatchingState.AUCTION);
        security.setMarketPrice(15820);
        security.getOrderBook().enqueue(new Order(1, security, Side.BUY, 600, 15800, broker, shareholder));
        security.getOrderBook().enqueue(new IcebergOrder(1, security, Side.SELL, 200, 15700, broker, shareholder, 50));
        assertThat(security.tradableQuantity()).isEqualTo(550);
    }
}