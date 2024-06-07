package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.validation.ValidationList;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.TradeDTO;
import ir.ramtung.tinyme.messaging.event.*;
import ir.ramtung.tinyme.messaging.request.*;
import ir.ramtung.tinyme.repository.BrokerRepository;
import ir.ramtung.tinyme.repository.Repositories;
import ir.ramtung.tinyme.repository.SecurityRepository;
import ir.ramtung.tinyme.repository.ShareholderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderHandler {
    SecurityRepository securityRepository;
    BrokerRepository brokerRepository;
    ShareholderRepository shareholderRepository;
    EventPublisher eventPublisher;
    Repositories repositories;
    Matcher matcher;

    @Autowired
    private ValidationList validations;

    public OrderHandler(SecurityRepository securityRepository, BrokerRepository brokerRepository, ShareholderRepository shareholderRepository, EventPublisher eventPublisher, Matcher matcher) {
        this.securityRepository = securityRepository;
        this.brokerRepository = brokerRepository;
        this.shareholderRepository = shareholderRepository;
        this.repositories = new Repositories(shareholderRepository,
                                             securityRepository,
                                             brokerRepository);
        this.eventPublisher = eventPublisher;
        this.matcher = matcher;
    }

    public void handleEnterOrder(EnterOrderRq enterOrderRq) {
        try {
            validations.validate(enterOrderRq, repositories);

            Security security = securityRepository.findSecurityByIsin(enterOrderRq.getSecurityIsin());

            MatchResult matchResult;
            if (enterOrderRq.getRequestType() == OrderEntryType.NEW_ORDER)
                matchResult = security.newOrder(createNewOrder(enterOrderRq), matcher);
            else
                matchResult = security.updateOrder(enterOrderRq, matcher);

            switch (matchResult.outcome()) {
                case NOT_ENOUGH_CREDIT:
                    eventPublisher.publish(new OrderRejectedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(), List.of(Message.BUYER_HAS_NOT_ENOUGH_CREDIT)));
                    return;
                case NOT_ENOUGH_POSITIONS:
                    eventPublisher.publish(new OrderRejectedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(), List.of(Message.SELLER_HAS_NOT_ENOUGH_POSITIONS)));
                    return;
                case MINIMUM_QUANTITY_NOT_SATISFIED:
                    eventPublisher.publish(new OrderRejectedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(), List.of(Message.ORDER_HAS_NOT_EXECUTED_MINIMUM_EXECUTION_QUANTITY)));
                    return;
                case NOT_EQUAL_MINIMUM_EXECUTION_QUANTITY:
                    eventPublisher.publish(new OrderRejectedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(), List.of(Message.MINIMUM_EXECUTION_QUANTITY_OF_UPDATE_ORDER_HAS_CHANGED)));
                    return;
                case QUEUED_DURING_AUCTION_STATE:
                    eventPublisher.publish(new OpeningPriceEvent(enterOrderRq.getSecurityIsin(), security.getOpeningPrice(), security.tradableQuantity()));
                    break;
            }

            if (enterOrderRq.getRequestType() == OrderEntryType.NEW_ORDER)
                eventPublisher.publish(new OrderAcceptedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId()));
            else
                eventPublisher.publish(new OrderUpdatedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId()));
            if (matchResult.outcome() != MatchingOutcome.NOT_ACTIVATABLE && enterOrderRq.getStopPrice() > 0)
                eventPublisher.publish(new OrderActivatedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId()));
            if (!matchResult.trades().isEmpty()) {
                eventPublisher.publish(new OrderExecutedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(), matchResult.trades().stream().map(TradeDTO::new).collect(Collectors.toList())));
            }
            checkNewActivation(security);
        } catch (InvalidRequestException ex) {
            eventPublisher.publish(new OrderRejectedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(), ex.getReasons()));
        }
    }

    public void checkNewActivation(Security security) {
        List<StopLimitOrder> activatedOrders = activateOrders(security);
        while (!activatedOrders.isEmpty())
            activatedOrders = executeActivatedOrders(security, activatedOrders);

    }

    private List<StopLimitOrder> activateOrders(Security security) {
        List<StopLimitOrder> activatedOrders = new LinkedList<>();
        StopLimitOrder order;
        while ((order = security.triggerOrder()) != null) {
            activatedOrders.add(order);
            eventPublisher.publish(new OrderActivatedEvent(order.getRequestId(), order.getOrderId()));
        }
        return activatedOrders;
    }

    private List<StopLimitOrder> executeActivatedOrders(Security security, List<StopLimitOrder> activatedOrders) {
        List<StopLimitOrder> nextActivatedOrders = new LinkedList<>();
        if (security.getMatchingState() == MatchingState.CONTINUOUS) {
            for (StopLimitOrder order : activatedOrders) {
                MatchResult matchResult = matcher.execute(order.activate());
                if (!matchResult.trades().isEmpty())
                    eventPublisher.publish(new OrderExecutedEvent(order.getRequestId(), order.getOrderId(), matchResult.trades().stream().map(TradeDTO::new).collect(Collectors.toList())));
                nextActivatedOrders.addAll(activateOrders(security));
            }
        } else {
            for (StopLimitOrder order : activatedOrders)
                security.getOrderBook().enqueue(order.activate());
        }
        return nextActivatedOrders;
    }

    public void handleDeleteOrder(DeleteOrderRq deleteOrderRq) {
        try {
            validations.validate(deleteOrderRq, repositories);
            Security security = securityRepository.findSecurityByIsin(deleteOrderRq.getSecurityIsin());
            Order order = security.getOrderBook().findByOrderId(deleteOrderRq.getSide(), deleteOrderRq.getOrderId());
            security.deleteOrder(order);
            eventPublisher.publish(new OrderDeletedEvent(deleteOrderRq.getRequestId(), deleteOrderRq.getOrderId()));
            if (security.getMatchingState() == MatchingState.AUCTION)
                eventPublisher.publish(new OpeningPriceEvent(deleteOrderRq.getSecurityIsin(), security.getOpeningPrice(), security.tradableQuantity()));
        } catch (InvalidRequestException ex) {
            eventPublisher.publish(new OrderRejectedEvent(deleteOrderRq.getRequestId(), deleteOrderRq.getOrderId(), ex.getReasons()));
        }
    }

    public void handleChangeMatchingState(ChangeMatchingStateRq changeMatchingStateRq) {
        try {
            validations.validate(changeMatchingStateRq, repositories);
            Security security = securityRepository.findSecurityByIsin(changeMatchingStateRq.getSecurityIsin());
            List<Trade> trades = security.changeMatchingState(changeMatchingStateRq, matcher);

            for (Trade trade : trades) {
                eventPublisher.publish(new TradeEvent(changeMatchingStateRq.getSecurityIsin(),
                        trade.getPrice(), trade.getQuantity(), trade.getBuy().getOrderId(), trade.getSell().getOrderId()));
            }
            eventPublisher.publish(new SecurityStateChangedEvent(changeMatchingStateRq.getSecurityIsin(), changeMatchingStateRq.getTargetState()));
            checkNewActivation(security);
        } catch (InvalidRequestException ignored) {
        }
    }

    private Order createNewOrder(EnterOrderRq enterOrderRq) {
        Security security = securityRepository.findSecurityByIsin(enterOrderRq.getSecurityIsin());
        Broker broker = brokerRepository.findBrokerById(enterOrderRq.getBrokerId());
        Shareholder shareholder = shareholderRepository.findShareholderById(enterOrderRq.getShareholderId());
        Order order;

        if (enterOrderRq.getPeakSize() == 0)
            if (enterOrderRq.getStopPrice() == 0)
                order = new Order(enterOrderRq.getOrderId(), security, enterOrderRq.getSide(),
                        enterOrderRq.getQuantity(), enterOrderRq.getPrice(), broker, shareholder,
                        enterOrderRq.getEntryTime(), enterOrderRq.getMinimumExecutionQuantity());
            else
                order = new StopLimitOrder(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(), security, enterOrderRq.getSide(),
                        enterOrderRq.getQuantity(), enterOrderRq.getPrice(), broker, shareholder,
                        enterOrderRq.getEntryTime(), enterOrderRq.getStopPrice());
        else
            order = new IcebergOrder(enterOrderRq.getOrderId(), security, enterOrderRq.getSide(),
                    enterOrderRq.getQuantity(), enterOrderRq.getPrice(), broker, shareholder,
                    enterOrderRq.getEntryTime(), enterOrderRq.getPeakSize(), enterOrderRq.getMinimumExecutionQuantity());
        return order;
    }

}