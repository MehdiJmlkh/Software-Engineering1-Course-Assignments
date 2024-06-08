package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.publisher.PublisherList;
import ir.ramtung.tinyme.domain.service.validation.ValidationList;
import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.request.*;
import ir.ramtung.tinyme.repository.BrokerRepository;
import ir.ramtung.tinyme.repository.Repositories;
import ir.ramtung.tinyme.repository.SecurityRepository;
import ir.ramtung.tinyme.repository.ShareholderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.List;

@Service
public class OrderHandler {
    SecurityRepository securityRepository;
    BrokerRepository brokerRepository;
    ShareholderRepository shareholderRepository;
    EventPublisher eventPublisher;
    Repositories repositories;
    Matcher matcher;
    OrderFactory orderFactory;

    @Autowired
    private ValidationList validations;
    @Autowired
    private PublisherList publishers;

    public OrderHandler(SecurityRepository securityRepository, BrokerRepository brokerRepository, ShareholderRepository shareholderRepository, EventPublisher eventPublisher, Matcher matcher) {
        this.securityRepository = securityRepository;
        this.brokerRepository = brokerRepository;
        this.shareholderRepository = shareholderRepository;
        this.repositories = new Repositories(shareholderRepository,
                                             securityRepository,
                                             brokerRepository);
        this.eventPublisher = eventPublisher;
        this.matcher = matcher;
        this.orderFactory =  OrderFactory.builder()
                .brokerRepository(brokerRepository)
                .securityRepository(securityRepository)
                .shareholderRepository(shareholderRepository)
                .build();
    }

    public void handleEnterOrder(EnterOrderRq enterOrderRq) {
        try {
            validations.validate(enterOrderRq, repositories);
            Security security = securityRepository.findSecurityByIsin(enterOrderRq.getSecurityIsin());
            MatchResult matchResult;
            if (enterOrderRq.getRequestType() == OrderEntryType.NEW_ORDER)
                matchResult = security.newOrder(orderFactory.createOrder(enterOrderRq), matcher);
            else
                matchResult = security.updateOrder(enterOrderRq, matcher);

            publishers.enterOrderRqHandled(enterOrderRq, matchResult, security, eventPublisher);
            if (matchResult.outcome().isError())
                return;

            checkNewActivation(security);
        } catch (InvalidRequestException ex) {
            publishers.invalidRequestExceptionOccured(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(), ex, eventPublisher);
        }
    }

    public void handleDeleteOrder(DeleteOrderRq deleteOrderRq) {
        try {
            validations.validate(deleteOrderRq, repositories);
            Security security = securityRepository.findSecurityByIsin(deleteOrderRq.getSecurityIsin());
            security.deleteOrder(deleteOrderRq);
            publishers.deleteOrderRqHandled(deleteOrderRq, security, eventPublisher);
        } catch (InvalidRequestException ex) {
            publishers.invalidRequestExceptionOccured(deleteOrderRq.getRequestId(), deleteOrderRq.getOrderId(), ex, eventPublisher);
        }
    }

    public void handleChangeMatchingState(ChangeMatchingStateRq changeMatchingStateRq) {
        try {
            validations.validate(changeMatchingStateRq, repositories);
            Security security = securityRepository.findSecurityByIsin(changeMatchingStateRq.getSecurityIsin());
            List<Trade> trades = security.changeMatchingState(changeMatchingStateRq, matcher);
            publishers.changeMatchingStateRqHandled(changeMatchingStateRq, trades, eventPublisher);
            checkNewActivation(security);
        } catch (InvalidRequestException ignored) {
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
            publishers.queuedOrderActivated(order, eventPublisher);
        }
        return activatedOrders;
    }

    private List<StopLimitOrder> executeActivatedOrders(Security security, List<StopLimitOrder> activatedOrders) {
        List<StopLimitOrder> nextActivatedOrders = new LinkedList<>();
        if (security.getMatchingState() == MatchingState.CONTINUOUS) {
            for (StopLimitOrder order : activatedOrders) {
                MatchResult matchResult = matcher.execute(order.activate());
                publishers.activatedOrderExecuted(order, matchResult, eventPublisher);
                nextActivatedOrders.addAll(activateOrders(security));
            }
        } else {
            for (StopLimitOrder order : activatedOrders)
                security.getOrderBook().enqueue(order.activate());
        }
        return nextActivatedOrders;
    }

}