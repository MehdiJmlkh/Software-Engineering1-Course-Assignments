package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.TradeDTO;
import ir.ramtung.tinyme.messaging.event.*;
import ir.ramtung.tinyme.messaging.request.*;
import ir.ramtung.tinyme.repository.BrokerRepository;
import ir.ramtung.tinyme.repository.SecurityRepository;
import ir.ramtung.tinyme.repository.ShareholderRepository;
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
    Matcher matcher;

    public OrderHandler(SecurityRepository securityRepository, BrokerRepository brokerRepository, ShareholderRepository shareholderRepository, EventPublisher eventPublisher, Matcher matcher) {
        this.securityRepository = securityRepository;
        this.brokerRepository = brokerRepository;
        this.shareholderRepository = shareholderRepository;
        this.eventPublisher = eventPublisher;
        this.matcher = matcher;
    }

    public void handleEnterOrder(EnterOrderRq enterOrderRq) {
        try {
            validateEnterOrderRq(enterOrderRq);

            Security security = securityRepository.findSecurityByIsin(enterOrderRq.getSecurityIsin());
            Broker broker = brokerRepository.findBrokerById(enterOrderRq.getBrokerId());
            Shareholder shareholder = shareholderRepository.findShareholderById(enterOrderRq.getShareholderId());

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
        }
        else {
            for (StopLimitOrder order : activatedOrders)
                security.getOrderBook().enqueue(order.activate());
        }
        return nextActivatedOrders;
    }

    public void handleDeleteOrder(DeleteOrderRq deleteOrderRq) {
        try {
            validateDeleteOrderRq(deleteOrderRq);
            Security security = securityRepository.findSecurityByIsin(deleteOrderRq.getSecurityIsin());
            security.deleteOrder(deleteOrderRq);
            eventPublisher.publish(new OrderDeletedEvent(deleteOrderRq.getRequestId(), deleteOrderRq.getOrderId()));
            if (security.getMatchingState() == MatchingState.AUCTION)
                eventPublisher.publish(new OpeningPriceEvent(deleteOrderRq.getSecurityIsin(), security.getOpeningPrice(), security.tradableQuantity()));
        } catch (InvalidRequestException ex) {
            eventPublisher.publish(new OrderRejectedEvent(deleteOrderRq.getRequestId(), deleteOrderRq.getOrderId(), ex.getReasons()));
        }
    }

    public void handleChangeMatchingState(ChangeMatchingStateRq changeMatchingStateRq) {
        try {
            validateChangeMatchingStateRq(changeMatchingStateRq);
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
            if(enterOrderRq.getStopPrice() == 0)
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

    private void validateEnterOrderRq(EnterOrderRq enterOrderRq) throws InvalidRequestException {
        List<String> errors = new LinkedList<>();
        if (enterOrderRq.getOrderId() <= 0)
            errors.add(Message.INVALID_ORDER_ID);
        if (enterOrderRq.getQuantity() <= 0)
            errors.add(Message.ORDER_QUANTITY_NOT_POSITIVE);
        if (enterOrderRq.getPrice() <= 0)
            errors.add(Message.ORDER_PRICE_NOT_POSITIVE);
        if (enterOrderRq.getMinimumExecutionQuantity() < 0)
            errors.add(Message.ORDER_MINIMUM_EXECUTION_QUANTITY_NOT_POSITIVE);
        if (enterOrderRq.getMinimumExecutionQuantity() > enterOrderRq.getQuantity())
            errors.add(Message.MINIMUM_EXECUTION_QUANTITY_NOT_LESS_THAN_OR_EQUAL_TO_QUANTITY);
        if (enterOrderRq.getStopPrice() > 0) {
            if (enterOrderRq.getMinimumExecutionQuantity() > 0)
                errors.add(Message.CANNOT_SPECIFY_MINIMUM_EXECUTION_QUANTITY_FOR_A_STOP_LIMIT_ORDER);
            if (enterOrderRq.getPeakSize() > 0)
                errors.add(Message.STOP_LIMIT_ORDER_CAN_NOT_BE_ICEBERG_ORDER);
        }

        Security security = securityRepository.findSecurityByIsin(enterOrderRq.getSecurityIsin());
        if (security == null)
            errors.add(Message.UNKNOWN_SECURITY_ISIN);
        else {
            if (enterOrderRq.getQuantity() % security.getLotSize() != 0)
                errors.add(Message.QUANTITY_NOT_MULTIPLE_OF_LOT_SIZE);
            if (enterOrderRq.getPrice() % security.getTickSize() != 0)
                errors.add(Message.PRICE_NOT_MULTIPLE_OF_TICK_SIZE);
            if (security.getMatchingState() == MatchingState.AUCTION) {
                if (enterOrderRq.getMinimumExecutionQuantity() > 0)
                    errors.add(Message.CANNOT_SPECIFY_MINIMUM_EXECUTION_QUANTITY_IN_THE_AUCTION_STATE);
                if (enterOrderRq.getStopPrice() != 0)
                    errors.add(Message.CANNOT_SUBMIT_OR_UPDATE_STOP_LIMIT_ORDER_IN_THE_AUCTION_STATE);
            }

        }
        if (brokerRepository.findBrokerById(enterOrderRq.getBrokerId()) == null)
            errors.add(Message.UNKNOWN_BROKER_ID);
        if (shareholderRepository.findShareholderById(enterOrderRq.getShareholderId()) == null)
            errors.add(Message.UNKNOWN_SHAREHOLDER_ID);
        if (enterOrderRq.getPeakSize() < 0 || enterOrderRq.getPeakSize() >= enterOrderRq.getQuantity())
            errors.add(Message.INVALID_PEAK_SIZE);
        if (!errors.isEmpty())
            throw new InvalidRequestException(errors);

        if (enterOrderRq.getRequestType() == OrderEntryType.UPDATE_ORDER) {
            Order order = security.getOrderBook().findByOrderId(enterOrderRq.getSide(), enterOrderRq.getOrderId());
            if (order == null)
                throw new InvalidRequestException(Message.ORDER_ID_NOT_FOUND);
            if ((order instanceof IcebergOrder) && enterOrderRq.getPeakSize() == 0)
                throw new InvalidRequestException(Message.INVALID_PEAK_SIZE);
            if (!(order instanceof IcebergOrder) && enterOrderRq.getPeakSize() != 0)
                throw new InvalidRequestException(Message.CANNOT_SPECIFY_PEAK_SIZE_FOR_A_NON_ICEBERG_ORDER);
            if (!(order instanceof StopLimitOrder) && enterOrderRq.getStopPrice() > 0)
                throw new InvalidRequestException(Message.CANNOT_SPECIFY_STOP_PRICE_FOR_A_ACTIVATED_ORDER);
        }
    }

    private void validateDeleteOrderRq(DeleteOrderRq deleteOrderRq) throws InvalidRequestException {
        List<String> errors = new LinkedList<>();
        if (deleteOrderRq.getOrderId() <= 0)
            errors.add(Message.INVALID_ORDER_ID);
        Security security = securityRepository.findSecurityByIsin(deleteOrderRq.getSecurityIsin());
        if (security == null)
            errors.add(Message.UNKNOWN_SECURITY_ISIN);
        else if (security.getMatchingState() == MatchingState.AUCTION)
            errors.add(Message.CANNOT_DELETE_STOP_LIMIT_ORDER_IN_THE_AUCTION_STATE);
        if (!errors.isEmpty())
            throw new InvalidRequestException(errors);
        Order order = security.getOrderBook().findByOrderId(deleteOrderRq.getSide(), deleteOrderRq.getOrderId());
        if (order == null)
            throw new InvalidRequestException(Message.ORDER_ID_NOT_FOUND);
    }

    private void validateChangeMatchingStateRq(ChangeMatchingStateRq changeMatchingStateRq) throws InvalidRequestException {
        List<String> errors = new LinkedList<>();
        if (securityRepository.findSecurityByIsin(changeMatchingStateRq.getSecurityIsin()) == null)
            errors.add(Message.UNKNOWN_SECURITY_ISIN);
        if (!errors.isEmpty())
            throw new InvalidRequestException(errors);
    }
}
