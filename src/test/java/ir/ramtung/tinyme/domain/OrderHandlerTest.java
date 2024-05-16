package ir.ramtung.tinyme.domain;

import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.Matcher;
import ir.ramtung.tinyme.domain.service.OrderHandler;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.TradeDTO;
import ir.ramtung.tinyme.messaging.event.*;
import ir.ramtung.tinyme.messaging.request.ChangeMatchingStateRq;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.messaging.request.MatchingState;
import ir.ramtung.tinyme.repository.BrokerRepository;
import ir.ramtung.tinyme.repository.SecurityRepository;
import ir.ramtung.tinyme.repository.ShareholderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SpringBootTest
@Import(MockedJMSTestConfig.class)
@DirtiesContext
public class OrderHandlerTest {
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
    private Shareholder shareholder;
    private Broker broker1;
    private Broker broker2;
    private Broker broker3;
    private List<Order> orders;

    @BeforeEach
    void setup() {
        securityRepository.clear();
        brokerRepository.clear();
        shareholderRepository.clear();

        security = Security.builder().isin("ABC").build();
        securityRepository.addSecurity(security);

        shareholder = Shareholder.builder().build();
        shareholder.incPosition(security, 100_000);
        shareholderRepository.addShareholder(shareholder);

        broker1 = Broker.builder().brokerId(1).build();
        broker2 = Broker.builder().brokerId(2).build();
        broker3 = Broker.builder().brokerId(2).build();
        brokerRepository.addBroker(broker1);
        brokerRepository.addBroker(broker2);
        brokerRepository.addBroker(broker3);
    }
    @Test
    void new_order_matched_completely_with_one_trade() {
        Order matchingBuyOrder = new Order(100, security, Side.BUY, 1000, 15500, broker1, shareholder);
        Order incomingSellOrder = new Order(200, security, Side.SELL, 300, 15450, broker2, shareholder);
        security.getOrderBook().enqueue(matchingBuyOrder);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 200, LocalDateTime.now(), Side.SELL, 300, 15450, 2, shareholder.getShareholderId(), 0));

        Trade trade = new Trade(security, matchingBuyOrder.getPrice(), incomingSellOrder.getQuantity(),
                matchingBuyOrder, incomingSellOrder);
        verify(eventPublisher).publish((new OrderAcceptedEvent(1, 200)));
        verify(eventPublisher).publish(new OrderExecutedEvent(1, 200, List.of(new TradeDTO(trade))));
    }

    @Test
    void new_order_queued_with_no_trade() {
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 200, LocalDateTime.now(), Side.SELL, 300, 15450, 2, shareholder.getShareholderId(), 0));
        verify(eventPublisher).publish(new OrderAcceptedEvent(1, 200));
    }
    @Test
    void new_order_matched_partially_with_two_trades() {
        Order matchingBuyOrder1 = new Order(100, security, Side.BUY, 300, 15500, broker1, shareholder);
        Order matchingBuyOrder2 = new Order(110, security, Side.BUY, 300, 15500, broker1, shareholder);
        Order incomingSellOrder = new Order(200, security, Side.SELL, 1000, 15450, broker2, shareholder);
        security.getOrderBook().enqueue(matchingBuyOrder1);
        security.getOrderBook().enqueue(matchingBuyOrder2);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1,
                incomingSellOrder.getSecurity().getIsin(),
                incomingSellOrder.getOrderId(),
                incomingSellOrder.getEntryTime(),
                incomingSellOrder.getSide(),
                incomingSellOrder.getTotalQuantity(),
                incomingSellOrder.getPrice(),
                incomingSellOrder.getBroker().getBrokerId(),
                incomingSellOrder.getShareholder().getShareholderId(), 0));

        Trade trade1 = new Trade(security, matchingBuyOrder1.getPrice(), matchingBuyOrder1.getQuantity(),
                matchingBuyOrder1, incomingSellOrder);
        Trade trade2 = new Trade(security, matchingBuyOrder2.getPrice(), matchingBuyOrder2.getQuantity(),
                matchingBuyOrder2, incomingSellOrder.snapshotWithQuantity(700));
        verify(eventPublisher).publish(new OrderAcceptedEvent(1, 200));
        verify(eventPublisher).publish(new OrderExecutedEvent(1, 200, List.of(new TradeDTO(trade1), new TradeDTO(trade2))));
    }

    @Test
    void iceberg_order_behaves_normally_before_being_queued() {
        Order matchingBuyOrder = new Order(100, security, Side.BUY, 1000, 15500, broker1, shareholder);
        Order incomingSellOrder = new IcebergOrder(200, security, Side.SELL, 300, 15450, broker2, shareholder, 100);
        security.getOrderBook().enqueue(matchingBuyOrder);
        Trade trade = new Trade(security, matchingBuyOrder.getPrice(), incomingSellOrder.getQuantity(),
                matchingBuyOrder, incomingSellOrder);

        EventPublisher mockEventPublisher = mock(EventPublisher.class, withSettings().verboseLogging());
        OrderHandler myOrderHandler = new OrderHandler(securityRepository, brokerRepository, shareholderRepository, mockEventPublisher, new Matcher());
        myOrderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1,
                incomingSellOrder.getSecurity().getIsin(),
                incomingSellOrder.getOrderId(),
                incomingSellOrder.getEntryTime(),
                incomingSellOrder.getSide(),
                incomingSellOrder.getTotalQuantity(),
                incomingSellOrder.getPrice(),
                incomingSellOrder.getBroker().getBrokerId(),
                incomingSellOrder.getShareholder().getShareholderId(), 100));

        verify(mockEventPublisher).publish(new OrderAcceptedEvent(1, 200));
        verify(mockEventPublisher).publish(new OrderExecutedEvent(1, 200, List.of(new TradeDTO(trade))));
    }

    @Test
    void invalid_new_order_with_multiple_errors() {
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "XXX", -1, LocalDateTime.now(), Side.SELL, 0, 0, -1, -1, 0));
        ArgumentCaptor<OrderRejectedEvent> orderRejectedCaptor = ArgumentCaptor.forClass(OrderRejectedEvent.class);
        verify(eventPublisher).publish(orderRejectedCaptor.capture());
        OrderRejectedEvent outputEvent = orderRejectedCaptor.getValue();
        assertThat(outputEvent.getOrderId()).isEqualTo(-1);
        assertThat(outputEvent.getErrors()).containsOnly(
                Message.UNKNOWN_SECURITY_ISIN,
                Message.INVALID_ORDER_ID,
                Message.ORDER_PRICE_NOT_POSITIVE,
                Message.ORDER_QUANTITY_NOT_POSITIVE,
                Message.INVALID_PEAK_SIZE,
                Message.UNKNOWN_BROKER_ID,
                Message.UNKNOWN_SHAREHOLDER_ID
        );
    }

    @Test
    void invalid_new_order_with_tick_and_lot_size_errors() {
        Security aSecurity = Security.builder().isin("XXX").lotSize(10).tickSize(10).build();
        securityRepository.addSecurity(aSecurity);
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "XXX", 1, LocalDateTime.now(), Side.SELL, 12, 1001, 1, shareholder.getShareholderId(), 0));
        ArgumentCaptor<OrderRejectedEvent> orderRejectedCaptor = ArgumentCaptor.forClass(OrderRejectedEvent.class);
        verify(eventPublisher).publish(orderRejectedCaptor.capture());
        OrderRejectedEvent outputEvent = orderRejectedCaptor.getValue();
        assertThat(outputEvent.getOrderId()).isEqualTo(1);
        assertThat(outputEvent.getErrors()).containsOnly(
                Message.QUANTITY_NOT_MULTIPLE_OF_LOT_SIZE,
                Message.PRICE_NOT_MULTIPLE_OF_TICK_SIZE
        );
    }

    @Test
    void update_order_causing_no_trades() {
        Order queuedOrder = new Order(200, security, Side.SELL, 500, 15450, broker1, shareholder);
        security.getOrderBook().enqueue(queuedOrder);
        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC", 200, LocalDateTime.now(), Side.SELL, 1000, 15450, 1, shareholder.getShareholderId(), 0));
        verify(eventPublisher).publish(new OrderUpdatedEvent(1, 200));
    }

    @Test
    void handle_valid_update_with_trades() {
        Order matchingOrder = new Order(1, security, Side.BUY, 500, 15450, broker1, shareholder);
        Order beforeUpdate = new Order(200, security, Side.SELL, 1000, 15455, broker2, shareholder);
        Order afterUpdate = new Order(200, security, Side.SELL, 500, 15450, broker2, shareholder);
        security.getOrderBook().enqueue(matchingOrder);
        security.getOrderBook().enqueue(beforeUpdate);

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC", 200, LocalDateTime.now(), Side.SELL, 1000, 15450, broker2.getBrokerId(), shareholder.getShareholderId(), 0));

        Trade trade = new Trade(security, 15450, 500, matchingOrder, afterUpdate);
        verify(eventPublisher).publish(new OrderUpdatedEvent(1, 200));
        verify(eventPublisher).publish(new OrderExecutedEvent(1, 200, List.of(new TradeDTO(trade))));
    }

    @Test
    void invalid_update_with_order_id_not_found() {
        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC", 200, LocalDateTime.now(), Side.SELL, 1000, 15450, 1, shareholder.getShareholderId(), 0));
        verify(eventPublisher).publish(new OrderRejectedEvent(1, 200, any()));
    }

    @Test
    void invalid_update_with_multiple_errors() {
        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "XXX", -1, LocalDateTime.now(), Side.SELL, 0, 0, -1, shareholder.getShareholderId(), 0));
        ArgumentCaptor<OrderRejectedEvent> orderRejectedCaptor = ArgumentCaptor.forClass(OrderRejectedEvent.class);
        verify(eventPublisher).publish(orderRejectedCaptor.capture());
        OrderRejectedEvent outputEvent = orderRejectedCaptor.getValue();
        assertThat(outputEvent.getOrderId()).isEqualTo(-1);
        assertThat(outputEvent.getErrors()).containsOnly(
                Message.UNKNOWN_SECURITY_ISIN,
                Message.UNKNOWN_BROKER_ID,
                Message.INVALID_ORDER_ID,
                Message.ORDER_PRICE_NOT_POSITIVE,
                Message.ORDER_QUANTITY_NOT_POSITIVE,
                Message.INVALID_PEAK_SIZE
        );
    }

    @Test
    void invalid_delete_with_order_id_not_found() {
        Broker buyBroker = Broker.builder().credit(1_000_000).build();
        brokerRepository.addBroker(buyBroker);
        Order queuedOrder = new Order(200, security, Side.BUY, 1000, 15500, buyBroker, shareholder);
        security.getOrderBook().enqueue(queuedOrder);
        orderHandler.handleDeleteOrder(new DeleteOrderRq(1, "ABC", Side.SELL, 100));
        verify(eventPublisher).publish(new OrderRejectedEvent(1, 100, List.of(Message.ORDER_ID_NOT_FOUND)));
        assertThat(buyBroker.getCredit()).isEqualTo(1_000_000);
    }

    @Test
    void invalid_delete_order_with_non_existing_security() {
        Order queuedOrder = new Order(200, security, Side.BUY, 1000, 15500, broker1, shareholder);
        security.getOrderBook().enqueue(queuedOrder);
        orderHandler.handleDeleteOrder(new DeleteOrderRq(1, "XXX", Side.SELL, 200));
        verify(eventPublisher).publish(new OrderRejectedEvent(1, 200, List.of(Message.UNKNOWN_SECURITY_ISIN)));
    }

    @Test
    void new_sell_order_without_enough_positions_is_rejected() {
        List<Order> orders = Arrays.asList(
                new Order(1, security, Side.BUY, 304, 570, broker3, shareholder),
                new Order(2, security, Side.BUY, 430, 550, broker3, shareholder),
                new Order(3, security, Side.BUY, 445, 545, broker3, shareholder),
                new Order(6, security, Side.SELL, 350, 580, broker1, shareholder),
                new Order(7, security, Side.SELL, 100, 581, broker2, shareholder)
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));
        shareholder.decPosition(security, 99_500);
        broker3.increaseCreditBy(100_000_000);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 200, LocalDateTime.now(), Side.SELL, 400, 590, broker1.getBrokerId(), shareholder.getShareholderId(), 0));

        verify(eventPublisher).publish(new OrderRejectedEvent(1, 200, List.of(Message.SELLER_HAS_NOT_ENOUGH_POSITIONS)));
    }

    @Test
    void update_sell_order_without_enough_positions_is_rejected() {
        List<Order> orders = Arrays.asList(
                new Order(1, security, Side.BUY, 304, 570, broker3, shareholder),
                new Order(2, security, Side.BUY, 430, 550, broker3, shareholder),
                new Order(3, security, Side.BUY, 445, 545, broker3, shareholder),
                new Order(6, security, Side.SELL, 350, 580, broker1, shareholder),
                new Order(7, security, Side.SELL, 100, 581, broker2, shareholder)
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));
        shareholder.decPosition(security, 99_500);
        broker3.increaseCreditBy(100_000_000);

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC", 6, LocalDateTime.now(), Side.SELL, 450, 580, broker1.getBrokerId(), shareholder.getShareholderId(), 0));

        verify(eventPublisher).publish(new OrderRejectedEvent(1, 6, List.of(Message.SELLER_HAS_NOT_ENOUGH_POSITIONS)));
    }

    @Test
    void update_sell_order_with_enough_positions_is_executed() {
        Shareholder shareholder1 = Shareholder.builder().build();
        shareholder1.incPosition(security, 100_000);
        shareholderRepository.addShareholder(shareholder1);
        List<Order> orders = Arrays.asList(
                new Order(1, security, Side.BUY, 304, 570, broker3, shareholder1),
                new Order(2, security, Side.BUY, 430, 550, broker3, shareholder1),
                new Order(3, security, Side.BUY, 445, 545, broker3, shareholder1),
                new Order(6, security, Side.SELL, 350, 580, broker1, shareholder),
                new Order(7, security, Side.SELL, 100, 581, broker2, shareholder)
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));
        shareholder.decPosition(security, 99_500);
        broker3.increaseCreditBy(100_000_000);

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC", 6, LocalDateTime.now(), Side.SELL, 250, 570, broker1.getBrokerId(), shareholder.getShareholderId(), 0));

        verify(eventPublisher).publish(any(OrderExecutedEvent.class));
        assertThat(shareholder1.hasEnoughPositionsOn(security, 100_000 + 250)).isTrue();
        assertThat(shareholder.hasEnoughPositionsOn(security, 99_500 - 251)).isFalse();
    }

    @Test
    void new_buy_order_does_not_check_for_position() {
        Shareholder shareholder1 = Shareholder.builder().build();
        shareholder1.incPosition(security, 100_000);
        shareholderRepository.addShareholder(shareholder1);
        List<Order> orders = Arrays.asList(
                new Order(1, security, Side.BUY, 304, 570, broker3, shareholder1),
                new Order(2, security, Side.BUY, 430, 550, broker3, shareholder1),
                new Order(3, security, Side.BUY, 445, 545, broker3, shareholder1),
                new Order(6, security, Side.SELL, 350, 580, broker1, shareholder),
                new Order(7, security, Side.SELL, 100, 581, broker2, shareholder)
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));
        shareholder.decPosition(security, 99_500);
        broker3.increaseCreditBy(100_000_000);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 200, LocalDateTime.now(), Side.BUY, 500, 570, broker3.getBrokerId(), shareholder.getShareholderId(), 0));

        verify(eventPublisher).publish(any(OrderAcceptedEvent.class));
        assertThat(shareholder1.hasEnoughPositionsOn(security, 100_000)).isTrue();
        assertThat(shareholder.hasEnoughPositionsOn(security, 500)).isTrue();
    }

    @Test
    void update_buy_order_does_not_check_for_position() {
        Shareholder shareholder1 = Shareholder.builder().build();
        shareholder1.incPosition(security, 100_000);
        shareholderRepository.addShareholder(shareholder1);
        List<Order> orders = Arrays.asList(
                new Order(1, security, Side.BUY, 304, 570, broker3, shareholder1),
                new Order(2, security, Side.BUY, 430, 550, broker3, shareholder1),
                new Order(3, security, Side.BUY, 445, 545, broker3, shareholder1),
                new Order(6, security, Side.SELL, 350, 580, broker1, shareholder),
                new Order(7, security, Side.SELL, 100, 581, broker2, shareholder)
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));
        shareholder.decPosition(security, 99_500);
        broker3.increaseCreditBy(100_000_000);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 3, LocalDateTime.now(), Side.BUY, 500, 545, broker3.getBrokerId(), shareholder1.getShareholderId(), 0));

        verify(eventPublisher).publish(any(OrderAcceptedEvent.class));
        assertThat(shareholder1.hasEnoughPositionsOn(security, 100_000)).isTrue();
        assertThat(shareholder.hasEnoughPositionsOn(security, 500)).isTrue();
    }

    @Test
    void invalid_new_order_with_minimum_execution_quantity_not_less_than_or_equal_to_quantity() {
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 3, LocalDateTime.now(), Side.BUY, 90, 545, broker3.getBrokerId(), shareholder.getShareholderId(), 0, 100));
        ArgumentCaptor<OrderRejectedEvent> orderRejectedCaptor = ArgumentCaptor.forClass(OrderRejectedEvent.class);
        verify(eventPublisher).publish(orderRejectedCaptor.capture());
        OrderRejectedEvent outputEvent = orderRejectedCaptor.getValue();
        assertThat(outputEvent.getErrors()).containsOnly(
                Message.MINIMUM_EXECUTION_QUANTITY_NOT_LESS_THAN_OR_EQUAL_TO_QUANTITY
        );
    }

    @Test
    void invalid_new_order_with_minimum_execution_quantity_not_positive() {
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 3, LocalDateTime.now(), Side.BUY, 90, 545, broker3.getBrokerId(), shareholder.getShareholderId(), 0, -100));
        ArgumentCaptor<OrderRejectedEvent> orderRejectedCaptor = ArgumentCaptor.forClass(OrderRejectedEvent.class);
        verify(eventPublisher).publish(orderRejectedCaptor.capture());
        OrderRejectedEvent outputEvent = orderRejectedCaptor.getValue();
        assertThat(outputEvent.getErrors()).containsOnly(
                Message.ORDER_MINIMUM_EXECUTION_QUANTITY_NOT_POSITIVE
        );
    }

    @Test
    void new_order_without_enough_execution_quantity_is_rejected() {
        List<Order> orders = Arrays.asList(
                new Order(1, security, Side.BUY, 20, 570, broker3, shareholder),
                new Order(2, security, Side.BUY, 430, 550, broker3, shareholder),
                new Order(3, security, Side.BUY, 445, 545, broker3, shareholder),
                new Order(6, security, Side.SELL, 350, 580, broker1, shareholder),
                new Order(7, security, Side.SELL, 100, 581, broker2, shareholder)
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));
        shareholder.decPosition(security, 99_500);
        broker3.increaseCreditBy(100_000_000);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 5, LocalDateTime.now(), Side.SELL, 40, 570, broker1.getBrokerId(), shareholder.getShareholderId(), 0,30));

        verify(eventPublisher).publish(new OrderRejectedEvent(1, 5, List.of(Message.ORDER_HAS_NOT_EXECUTED_MINIMUM_EXECUTION_QUANTITY)));
    }

    @Test
    void update_order_with_different_minimum_execution_quantity_is_rejected() {
        List<Order> orders = Arrays.asList(
                new Order(1, security, Side.BUY, 20, 570, broker3, shareholder, 10),
                new Order(2, security, Side.BUY, 430, 550, broker3, shareholder),
                new Order(3, security, Side.BUY, 445, 545, broker3, shareholder),
                new Order(6, security, Side.SELL, 350, 580, broker1, shareholder),
                new Order(7, security, Side.SELL, 100, 581, broker2, shareholder)
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));
        shareholder.decPosition(security, 99_500);
        broker3.increaseCreditBy(100_000_000);

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC", 1, LocalDateTime.now(), Side.BUY, 40, 570, broker3.getBrokerId(), shareholder.getShareholderId(), 0,30));

        verify(eventPublisher).publish(new OrderRejectedEvent(1, 1, List.of(Message.MINIMUM_EXECUTION_QUANTITY_OF_UPDATE_ORDER_HAS_CHANGED)));
    }

    void setupOrderBook() {
        orders = Arrays.asList(
                new Order(1, security, Side.BUY, 304, 15700, broker1, shareholder),
                new Order(2, security, Side.BUY, 43, 15500, broker1, shareholder),
                new Order(3, security, Side.BUY, 445, 15450, broker1, shareholder),
                new Order(4, security, Side.BUY, 526, 15450, broker1, shareholder),
                new Order(5, security, Side.BUY, 1000, 15400, broker1, shareholder),
                new Order(6, security, Side.SELL, 350, 15800, broker1, shareholder),
                new Order(7, security, Side.SELL, 285, 15810, broker1, shareholder),
                new Order(8, security, Side.SELL, 800, 15810, broker1, shareholder),
                new Order(9, security, Side.SELL, 340, 15820, broker1, shareholder),
                new Order(10, security, Side.SELL, 65, 15820, broker1, shareholder),
                new StopLimitOrder(1, 11, security, Side.BUY, 340, 15750, broker1, shareholder, 15700),
                new StopLimitOrder(2, 12, security, Side.BUY, 200, 15850, broker1, shareholder, 15800),
                new StopLimitOrder(3, 13, security, Side.BUY, 500, 15850, broker1, shareholder, 15850),
                new StopLimitOrder(4, 14, security, Side.SELL, 320, 15500, broker1, shareholder, 15600),
                new StopLimitOrder(5, 15, security, Side.SELL, 85, 15350, broker1, shareholder, 15500),
                new StopLimitOrder(6, 16, security, Side.SELL, 85, 15300, broker1, shareholder, 15400)
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));
        broker1.increaseCreditBy(100_000_000);
        security.setMarketPrice(15650);
    }

    @Test
    void stop_limit_order_can_not_have_minimum_execution_quantity(){
        setupOrderBook();
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC",20, LocalDateTime.now(), Side.SELL,2000, 15800,broker1.getBrokerId(), shareholder.getShareholderId(), 0, 200, 15000));
        verify(eventPublisher).publish(new OrderRejectedEvent(1, 20, List.of(Message.CANNOT_SPECIFY_MINIMUM_EXECUTION_QUANTITY_FOR_A_STOP_LIMIT_ORDER)));
    }

    @Test
    void stop_limit_order_can_not_be_ice_burg_order(){
        setupOrderBook();
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC",20, LocalDateTime.now(), Side.SELL,2000, 15800,broker1.getBrokerId(), shareholder.getShareholderId(), 200, 0, 15000));
        verify(eventPublisher).publish(new OrderRejectedEvent(1, 20, List.of(Message.STOP_LIMIT_ORDER_CAN_NOT_BE_ICEBERG_ORDER)));
    }

    @Test
    void after_a_new_request_two_buy_stop_limit_order_triggered_and_the_second_one_executed() {
        setupOrderBook();
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(6, "ABC", 17, LocalDateTime.now(), Side.BUY, 50, 15800, broker1.getBrokerId(), shareholder.getShareholderId(), 0));

        verify(eventPublisher).publish(new OrderAcceptedEvent(6, 17));
        verify(eventPublisher).publish(new OrderActivatedEvent(1, 11));
        verify(eventPublisher).publish(new OrderActivatedEvent(2, 12));

        Trade trade1 = new Trade(security, 15800, 200, orders.get(11), orders.get(5));
        verify(eventPublisher).publish(new OrderExecutedEvent(2, 12, List.of(new TradeDTO(trade1))));

    }

    @Test
    void after_a_new_request_two_sell_stop_limit_order_triggered_and_the_second_one_executed() {
        setupOrderBook();
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(6, "ABC", 17, LocalDateTime.now(), Side.SELL, 2000, 15500, broker1.getBrokerId(), shareholder.getShareholderId(), 0));

        verify(eventPublisher).publish(new OrderAcceptedEvent(6, 17));
        verify(eventPublisher).publish(new OrderActivatedEvent(4, 14));
        verify(eventPublisher).publish(new OrderActivatedEvent(5, 15));

        Trade trade1 = new Trade(security, 15450, 85, orders.get(2), orders.get(14));
        verify(eventPublisher).publish(new OrderExecutedEvent(5, 15, List.of(new TradeDTO(trade1))));
    }

    @Test
    void sell_stop_limit_order_triggered_after_updating() {
        setupOrderBook();
        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC", 16, LocalDateTime.now(), Side.SELL, 2000, 15400, broker1.getBrokerId(), shareholder.getShareholderId(), 0,0,15650));
        verify(eventPublisher).publish(new OrderActivatedEvent(1, 16));
    }

    @Test
    void update_stop_price_of_triggered_order_rejected() {
        setupOrderBook();
        broker1.increaseCreditBy(100_000_000);
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 17, LocalDateTime.now(), Side.BUY, 50, 15800, broker1.getBrokerId(), shareholder.getShareholderId(), 0));
        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC", 11, LocalDateTime.now(), Side.BUY, 2000, 15400, broker1.getBrokerId(), shareholder.getShareholderId(), 0,0,15650));
        verify(eventPublisher).publish(new OrderRejectedEvent(1, 11, List.of(Message.CANNOT_SPECIFY_STOP_PRICE_FOR_A_ACTIVATED_ORDER)));
    }

    @Test
    void stop_price_update_does_not_trigger_the_order() {
        setupOrderBook();
        broker1.increaseCreditBy(100_000_000);
        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC", 16, LocalDateTime.now(), Side.SELL, 2000, 15400, broker1.getBrokerId(), shareholder.getShareholderId(), 0,0,15600));
        assertThat(security.getOrderBook().getStopSellQueue()).isEqualTo(Arrays.asList(orders.get(13), orders.get(15), orders.get(14)));
    }

    @Test
    void update_price_of_stop_limit_order_does_not_change_anything() {
        setupOrderBook();
        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC", 16, LocalDateTime.now(), Side.SELL, 2000, 15400, broker1.getBrokerId(), shareholder.getShareholderId(), 0,0));
        assertThat(security.getOrderBook().getStopSellQueue()).isEqualTo(orders.subList(13, 16));
    }

    @Test
    void delete_stop_limit_order(){
        setupOrderBook();
        orderHandler.handleDeleteOrder(new DeleteOrderRq(2, "ABC", Side.BUY, 11));
        verify(eventPublisher).publish(new OrderDeletedEvent(2, 11));
    }

    @Test
    void change_matching_state_to_auction_works(){
        orderHandler.handleChangeMatchingState(new ChangeMatchingStateRq("ABC", MatchingState.AUCTION));
        verify(eventPublisher).publish(new SecurityStateChangedEvent("ABC", MatchingState.AUCTION));
    }

    @Test
    void change_matching_state_to_continuous_works(){
        orderHandler.handleChangeMatchingState(new ChangeMatchingStateRq("ABC", MatchingState.CONTINUOUS));
        verify(eventPublisher).publish(new SecurityStateChangedEvent("ABC", MatchingState.CONTINUOUS));
    }

    @Test
    void change_matching_state_causes_trades() {
        setupOrderBook();
        security.setMatchingState(MatchingState.AUCTION);
        List<Order> tradableOrders = Arrays.asList(
                new Order(17, security, Side.BUY, 445, 15950, broker1, shareholder),
                new Order(18, security, Side.SELL, 1000, 15650, broker1, shareholder)
        );
        tradableOrders.forEach(order -> security.getOrderBook().enqueue(order));
        orderHandler.handleChangeMatchingState(new ChangeMatchingStateRq("ABC", MatchingState.CONTINUOUS));

        verify(eventPublisher).publish(new SecurityStateChangedEvent("ABC", MatchingState.CONTINUOUS));
        verify(eventPublisher).publish(new TradeEvent("ABC", 15650, 445, 17, 18));
        verify(eventPublisher).publish(new TradeEvent("ABC", 15650, 304, 1, 18));
    }

    @Test
    void changing_matching_state_to_auction_activates_two_buy_stop_limit_orders_but_not_executed(){
        setupOrderBook();
        security.setMatchingState(MatchingState.AUCTION);
        security.getOrderBook().enqueue(
                new Order(17, security, Side.BUY, 445, 15800, broker1, shareholder)
        );
        orderHandler.handleChangeMatchingState(new ChangeMatchingStateRq(security.getIsin(), MatchingState.AUCTION));
        verify(eventPublisher).publish(new SecurityStateChangedEvent("ABC", MatchingState.AUCTION));
        verify(eventPublisher).publish(new TradeEvent("ABC", 15800, 350, 17, 6));
        verify(eventPublisher).publish(new OrderActivatedEvent(1, 11));
        verify(eventPublisher).publish(new OrderActivatedEvent(2, 12));
        assertThat(security.getOrderBook().getBuyQueue().getFirst()).isEqualTo(orders.get(11));
        assertThat(security.getOrderBook().getBuyQueue().get(2)).isEqualTo(orders.get(10));
    }

    @Test
    void changing_matching_state_to_auction_activates_three_sell_stop_limit_orders_but_not_executed(){
        setupOrderBook();
        security.setMarketPrice(15200);
        security.setMatchingState(MatchingState.AUCTION);
        security.getOrderBook().enqueue(
                new Order(17, security, Side.SELL, 305, 15300, broker1, shareholder)
        );
        orderHandler.handleChangeMatchingState(new ChangeMatchingStateRq(security.getIsin(), MatchingState.AUCTION));

        LinkedList<Order> sellQueue = new LinkedList<>(List.of(orders.get(15), orders.get(14), orders.get(13)));
        sellQueue.addAll(orders.subList(5, 10));

        verify(eventPublisher).publish(new TradeEvent("ABC", 15300, 304, 1, 17));
        verify(eventPublisher).publish(new TradeEvent("ABC", 15300, 1, 2, 17));
        verify(eventPublisher).publish(new OrderActivatedEvent(4, 14));
        verify(eventPublisher).publish(new OrderActivatedEvent(5, 15));
        verify(eventPublisher).publish(new OrderActivatedEvent(6, 16));
        assertThat(security.getOrderBook().getSellQueue()).isEqualTo(sellQueue);
    }

    @Test
    void changing_matching_state_to_continuous_activates_two_buy_stop_limit_orders_and_executed(){
        setupOrderBook();
        security.setMatchingState(MatchingState.AUCTION);
        security.getOrderBook().enqueue(
                new Order(17, security, Side.BUY, 445, 15800, broker1, shareholder)
        );
        orderHandler.handleChangeMatchingState(new ChangeMatchingStateRq(security.getIsin(), MatchingState.CONTINUOUS));
        verify(eventPublisher).publish(new SecurityStateChangedEvent("ABC", MatchingState.CONTINUOUS));

        Trade trade = new Trade(security, 15810, 200, orders.get(11), orders.get(6));
        verify(eventPublisher).publish(new OrderExecutedEvent(2, 12, List.of(new TradeDTO(trade))));
    }


    @Test
    void changing_matching_state_to_continuous_activates_three_sell_stop_limit_orders_and_executed(){
        setupOrderBook();
        security.setMarketPrice(15200);
        security.setMatchingState(MatchingState.AUCTION);
        security.getOrderBook().enqueue(
                new Order(17, security, Side.SELL, 305, 15300, broker1, shareholder)
        );
        orderHandler.handleChangeMatchingState(new ChangeMatchingStateRq(security.getIsin(), MatchingState.CONTINUOUS));

        verify(eventPublisher).publish(new SecurityStateChangedEvent("ABC", MatchingState.CONTINUOUS));
        verify(eventPublisher).publish(new TradeEvent("ABC", 15300, 304, 1, 17));
        verify(eventPublisher).publish(new TradeEvent("ABC", 15300, 1, 2, 17));
        verify(eventPublisher).publish(new OrderActivatedEvent(4, 14));
        verify(eventPublisher).publish(new OrderActivatedEvent(5, 15));
        verify(eventPublisher).publish(new OrderActivatedEvent(6, 16));

        Trade trade1 = new Trade(security, 15500, 42, orders.get(1), orders.get(13));
        Trade trade2 = new Trade(security, 15450, 85, orders.get(2), orders.get(14));
        Trade trade3 = new Trade(security, 15450, 85, orders.get(2).snapshotWithQuantity(370), orders.get(15));

        verify(eventPublisher).publish(new OrderExecutedEvent(4, 14, List.of(new TradeDTO(trade1))));
        verify(eventPublisher).publish(new OrderExecutedEvent(5, 15, List.of(new TradeDTO(trade2))));
        verify(eventPublisher).publish(new OrderExecutedEvent(6, 16, List.of(new TradeDTO(trade3))));
    }

    @Test
    void enter_new_sell_order_request_in_auction_state() {
        setupOrderBook();
        security.setMatchingState(MatchingState.AUCTION);
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 17, LocalDateTime.now(), Side.SELL, 50, 15700, broker1.getBrokerId(), shareholder.getShareholderId(), 0));
        eventPublisher.publish(new OpeningPriceEvent("ABC", 15700, 50));
        assertThat(security.getOrderBook().getSellQueue().getFirst().getOrderId()).isEqualTo(17);
        assertThat(security.getOrderBook().getSellQueue().getFirst().getQuantity()).isEqualTo(50);
    }

    @Test
    void enter_new_buy_order_request_in_auction_state() {
        setupOrderBook();
        security.setMatchingState(MatchingState.AUCTION);
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 17, LocalDateTime.now(), Side.BUY, 50, 15800, broker1.getBrokerId(), shareholder.getShareholderId(), 0));
        eventPublisher.publish(new OpeningPriceEvent("ABC", 15800, 50));
        assertThat(security.getOrderBook().getBuyQueue().getFirst().getOrderId()).isEqualTo(17);
        assertThat(security.getOrderBook().getBuyQueue().getFirst().getQuantity()).isEqualTo(50);
    }

}
