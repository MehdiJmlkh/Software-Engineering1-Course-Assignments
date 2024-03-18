package ir.ramtung.tinyme.domain;

import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.Matcher;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(MockedJMSTestConfig.class)
@DirtiesContext
public class BrokerCreditTest {
    private Security security;
    private Broker broker1;
    private Broker broker2;
    private Shareholder shareholder;
    private OrderBook orderBook;
    private List<Order> orders;
    @Autowired
    private Matcher matcher;

    @BeforeEach
    void setupOrderBook() {
        security = Security.builder().build();
        broker1 = Broker.builder().credit(1_000_000_000L).build();
        broker2 = Broker.builder().credit(1_000_000_000L).build();
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
        assertThat(result.remainder().getBroker().getCredit()).isEqualTo(1_000_157_000L);
        assertThat(broker1.getCredit()).isEqualTo(1_000_000_000L);
    }

    @Test
    void new_sell_order_matches_partially_with_the_first_buy() {
        Order order = new Order(11, security, Side.SELL, 500, 15600, broker1, shareholder);
        MatchResult result = matcher.match(order);
        assertThat(broker1.getCredit()).isEqualTo(1_004_772_800L);
        assertThat(broker2.getCredit()).isEqualTo(1_000_000_000L);
    }

}
