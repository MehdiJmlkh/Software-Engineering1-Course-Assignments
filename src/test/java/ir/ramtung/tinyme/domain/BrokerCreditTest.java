package ir.ramtung.tinyme.domain;

import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.Matcher;
import ir.ramtung.tinyme.domain.service.OrderHandler;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.event.OrderRejectedEvent;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.messaging.request.MatchingState;
import ir.ramtung.tinyme.messaging.request.OrderEntryType;
import ir.ramtung.tinyme.repository.BrokerRepository;
import ir.ramtung.tinyme.repository.SecurityRepository;
import ir.ramtung.tinyme.repository.ShareholderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Import(MockedJMSTestConfig.class)
@DirtiesContext
public class BrokerCreditTest {
    @Autowired
    OrderHandler orderHandler;
    @Autowired
    EventPublisher eventPublisher;
    @Autowired
    SecurityRepository securityRepository;
    @Autowired
    BrokerRepository brokerRepository;
    @Autowired
    ShareholderRepository shareholderRepository;
    private Security security;
    private Broker broker1;
    private Broker broker2;
    private Broker broker3;
    private Shareholder shareholder;
    private OrderBook orderBook;
    private List<Order> orders;
    @Autowired
    private Matcher matcher;

    @BeforeEach
    void setupOrderBook() {
        security = Security.builder().isin("ABC").build();
        securityRepository.addSecurity(security);
        broker1 = Broker.builder().credit(100_000_000L).build();
        broker2 = Broker.builder().credit(100_000_000L).build();
        broker3 = Broker.builder().credit(0).build();
        shareholder = Shareholder.builder().build();
        shareholder.incPosition(security, 100_000);
        orderBook = security.getOrderBook();
        orders = Arrays.asList(
                new Order(1, security, Side.BUY, 304, 15700, broker1, shareholder),
                new Order(2, security, Side.BUY, 43, 15500, broker1, shareholder),
                new Order(3, security, Side.BUY, 445, 15450, broker1, shareholder),
                new Order(4, security, Side.BUY, 526, 15450, broker2, shareholder),
                new Order(5, security, Side.BUY, 1000, 15400, broker2, shareholder),
                new Order(6, security, Side.SELL, 350, 15800, broker1, shareholder),
                new Order(7, security, Side.SELL, 285, 15810, broker1, shareholder),
                new Order(8, security, Side.SELL, 800, 15810, broker1, shareholder),
                new Order(9, security, Side.SELL, 340, 15820, broker2, shareholder),
                new Order(10, security, Side.SELL, 65, 15820, broker2, shareholder)
        );
        orders.forEach(order -> orderBook.enqueue(order));
    }

    @Test
    void new_sell_order_matches_completely_with_part_of_the_first_buy() {
        Order order = new Order(11, security, Side.SELL, 10, 15600, broker2, shareholder);
        MatchResult result = matcher.match(order);
        assertThat(result.remainder().getBroker().getCredit()).isEqualTo(100_157_000L);
        assertThat(broker1.getCredit()).isEqualTo(100_000_000L);
    }

    @Test
    void new_sell_order_matches_partially_with_the_first_buy() {
        Order order = new Order(11, security, Side.SELL, 500, 15600, broker1, shareholder);
        MatchResult result = matcher.match(order);
        assertThat(broker1.getCredit()).isEqualTo(104_772_800L);
        assertThat(broker2.getCredit()).isEqualTo(100_000_000L);
    }
    @Test
    void new_sell_order_matches_partially_with_two_buys() {
        Order order = new Order(11, security, Side.SELL, 500, 15500, broker1, shareholder);
        MatchResult result = matcher.match(order);
        assertThat(broker1.getCredit()).isEqualTo(105_439_300L);
        assertThat(broker2.getCredit()).isEqualTo(100_000_000L);
    }

    @Test
    void new_buy_order_matches_completely_with_part_of_the_first_buy_with_different_brokers() {
        Order order = new Order(11, security, Side.BUY, 10, 15830, broker2, shareholder);
        MatchResult result = matcher.match(order);
        assertThat(broker2.getCredit()).isEqualTo(99_842_000L);
        assertThat(broker1.getCredit()).isEqualTo(100_158_000L);
    }

    @Test
    void new_buy_order_matches_completely_with_part_of_the_first_buy_with_same_broker() {
        Order order = new Order(11, security, Side.BUY, 10, 15830, broker1, shareholder);
        MatchResult result = matcher.match(order);
        assertThat(broker2.getCredit()).isEqualTo(100_000_000L);
        assertThat(broker1.getCredit()).isEqualTo(100_000_000L);
    }

    @Test
    void new_buy_order_matches_completely_with_two_sells() {
        Order order = new Order(11, security, Side.BUY, 500, 15830, broker2, shareholder);
        MatchResult result = matcher.match(order);
        assertThat(broker1.getCredit()).isEqualTo(107_901_500L);
        assertThat(broker2.getCredit()).isEqualTo(92_098_500L);
    }

    @Test
    void new_buy_order_matches_partially_with_the_entire_sell_queue() {
        Order order = new Order(11, security, Side.BUY, 2000, 15820, broker2, shareholder);
        MatchResult result = matcher.match(order);
        assertThat(broker1.getCredit()).isEqualTo(122_683_850L);
        assertThat(broker2.getCredit()).isEqualTo(77_316_150L);
    }

    @Test
    void delete_sell_order() {
        try {
            DeleteOrderRq deleteOrderRq = new DeleteOrderRq(1, security.getIsin(), Side.SELL, 6);
            security.deleteOrder(deleteOrderRq);
            assertThat(broker1.getCredit()).isEqualTo(100_000_000L);
            assertThat(broker2.getCredit()).isEqualTo(100_000_000L);
        } catch (Exception ignored) {}
    }

    @Test
    void delete_buy_order() {
        try {
            DeleteOrderRq deleteOrderRq = new DeleteOrderRq(1, security.getIsin(), Side.BUY, 3);
            security.deleteOrder(deleteOrderRq);
            assertThat(broker1.getCredit()).isEqualTo(106_875_250L);
            assertThat(broker2.getCredit()).isEqualTo(100_000_000L);
        } catch (Exception ignored) {}
    }

    @Test
    void update_buy_order() {
        EnterOrderRq updateOrderRq = EnterOrderRq.createUpdateOrderRq(1, security.getIsin(), 5, LocalDateTime.now(), Side.BUY, 500, 15700, 0, 0, 0);
       try {
           security.updateOrder(updateOrderRq, matcher);
           assertThat(broker1.getCredit()).isEqualTo(100_000_000L);
           assertThat(broker2.getCredit()).isEqualTo(107_550_000L);
       } catch (Exception ignored) {}
    }

    @Test
    void update_sell_order() {
        EnterOrderRq updateOrderRq = EnterOrderRq.createUpdateOrderRq(1, security.getIsin(), 6, LocalDateTime.now(), Side.SELL, 500, 15700, 0, 0, 0);
        try {
            security.updateOrder(updateOrderRq, matcher);
            assertThat(broker2.getCredit()).isEqualTo(100_000_000L);
            assertThat(broker1.getCredit()).isEqualTo(104_772_800L);
        } catch (Exception ignored) {}
    }
    @Test
    void new_buy_order_does_not_have_enough_credit() {
        broker2 = Broker.builder().credit(6_000_000L).build();
        Order order = new Order(11, security, Side.BUY, 1000, 15810, broker2, shareholder);
        MatchResult result = matcher.match(order);
        assertThat(broker2.getCredit()).isEqualTo(6_000_000L);
        assertThat(broker1.getCredit()).isEqualTo(100_000_000L);
    }

    @Test
    void new_buy_order_does_not_match_minimum_execution_quantity() {
        Order order = new Order(11, security, Side.BUY, 2000, 15800, broker2, shareholder, 400);
        MatchResult result = matcher.execute(order);

        assertThat(broker2.getCredit()).isEqualTo(100_000_000L);
        assertThat(broker1.getCredit()).isEqualTo(100_000_000L);
    }

    @Test
    void new_sell_order_does_not_match_minimum_execution_quantity() {
        Order order = new Order(11, security, Side.SELL, 2000, 15700, broker2, shareholder, 400);
        MatchResult result = matcher.execute(order);

        assertThat(broker2.getCredit()).isEqualTo(100_000_000L);
        assertThat(broker1.getCredit()).isEqualTo(100_000_000L);
    }

    @Test
    void buyer_has_not_enough_credit_for_stop_order(){
        broker3.increaseCreditBy(15300);
        Order order = new Order(1, security, Side.SELL, 304, 15650, broker1, shareholder);
        StopLimitOrder stopLimitOrder = new StopLimitOrder(1, 20, security, Side.BUY, 2000, 15700, broker3, shareholder, 15300);
        MatchResult result = matcher.execute(stopLimitOrder);
        assertThat(result.outcome()).isEqualTo(MatchingOutcome.NOT_ENOUGH_CREDIT);
    }

    @Test
    void add_and_delete_buy_stop_limit_order(){
        security.setMarketPrice(15500);
        StopLimitOrder stopLimitOrder = new StopLimitOrder(1, 20, security, Side.BUY, 100, 15700, broker1, shareholder, 15600);
        matcher.execute(stopLimitOrder);
        assertThat(broker1.getCredit()).isEqualTo(98_430_000L);
        try {
            security.deleteOrder(new DeleteOrderRq(1, security.getIsin(), Side.BUY, 20));
            assertThat(broker1.getCredit()).isEqualTo(100_000_000L);
        } catch (Exception ignored) {}
    }

    @Test
    void add_and_delete_sell_stop_limit_order(){
        security.setMarketPrice(15500);
        StopLimitOrder stopLimitOrder = new StopLimitOrder(1, 20, security, Side.SELL, 100, 15700, broker1, shareholder, 15400);
        matcher.execute(stopLimitOrder);
        assertThat(broker1.getCredit()).isEqualTo(100_000_000L);
        try {
            security.deleteOrder(new DeleteOrderRq(1, security.getIsin(), Side.SELL, 20));
            assertThat(broker1.getCredit()).isEqualTo(100_000_000L);
        } catch (Exception ignored) {}
    }

    @Test
    void new_buy_stop_limit_order_matches_completely_with_two_sells() {
        security.setMarketPrice(15600);
        Order order = new StopLimitOrder(1, 11, security, Side.BUY, 500, 15830, broker2, shareholder, 15500);
        MatchResult result = matcher.execute(order);
        assertThat(broker1.getCredit()).isEqualTo(107_901_500L);
        assertThat(broker2.getCredit()).isEqualTo(92_098_500L);
    }

    @Test
    void new_sell_stop_limit_order_matches_partially_with_two_buys() {
        security.setMarketPrice(15400);
        Order order = new StopLimitOrder(1, 11, security, Side.SELL, 500, 15500, broker1, shareholder, 15500);
        matcher.execute(order);
        assertThat(broker1.getCredit()).isEqualTo(105_439_300L);
        assertThat(broker2.getCredit()).isEqualTo(100_000_000L);
    }

    @Test
    void new_sell_stop_limit_order_cannot_activate() {
        security.setMarketPrice(15600);
        Order order = new StopLimitOrder(1, 11, security, Side.SELL, 500, 15830, broker2, shareholder, 15500);
        MatchResult result = matcher.execute(order);

        assertThat(broker1.getCredit()).isEqualTo(100_000_000L);
        assertThat(broker2.getCredit()).isEqualTo(100_000_000L);
    }

    @Test
    void sell_stop_limit_order_triggered() {
        security.setMarketPrice(15600);
        Order order = new StopLimitOrder(1, 11, security, Side.SELL, 300, 15650, broker2, shareholder, 15500);
        MatchResult result = matcher.execute(order);
        security.setMarketPrice(15500);
        orderHandler.checkNewActivation(EnterOrderRq.createUpdateOrderRq(1,"ABC", 1, LocalDateTime.now(), Side.BUY, 2000, 15400, broker1.getBrokerId(), shareholder.getShareholderId(), 0,0, 2000));
        assertThat(broker1.getCredit()).isEqualTo(100_000_000L);
        assertThat(broker2.getCredit()).isEqualTo(104_710_000L);
    }

    @Test
    void buy_stop_limit_order_triggered() {
        security.setMarketPrice(15300);
        Order order = new StopLimitOrder(1, 11, security, Side.BUY, 300, 15850, broker2, shareholder, 15500);
        MatchResult result = matcher.execute(order);
        security.setMarketPrice(15500);
        orderHandler.checkNewActivation(EnterOrderRq.createUpdateOrderRq(1,"ABC", 1, LocalDateTime.now(), Side.BUY, 2000, 15400, broker1.getBrokerId(), shareholder.getShareholderId(), 0,0, 2000));
        assertThat(broker1.getCredit()).isEqualTo(104_740_000L);
        assertThat(broker2.getCredit()).isEqualTo(95_260_000L);
    }
    @Test
    void credit_of_buyer_is_decreased_when_auction(){
        security.setMatchingState(MatchingState.AUCTION);
        security.newOrder(EnterOrderRq.createNewOrderRq(1, security.getIsin(), 11, LocalDateTime.now(), Side.BUY, 5000, 1000, broker2.getBrokerId(), shareholder.getShareholderId(), 0), broker2, shareholder, matcher);
        assertThat(broker2.getCredit()).isEqualTo(95_000_000L);
    }
    @Test
    void credit_of_seller_does_not_change_when_auction(){
        security.setMatchingState(MatchingState.AUCTION);
        security.newOrder(EnterOrderRq.createNewOrderRq(1, security.getIsin(), 11, LocalDateTime.now(), Side.SELL, 5000, 1000, broker2.getBrokerId(), shareholder.getShareholderId(), 0), broker2, shareholder, matcher);
        assertThat(broker2.getCredit()).isEqualTo(100_000_000L);
    }

}
