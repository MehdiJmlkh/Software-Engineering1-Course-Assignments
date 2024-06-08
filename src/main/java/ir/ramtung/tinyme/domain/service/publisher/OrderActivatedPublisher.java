package ir.ramtung.tinyme.domain.service.publisher;

import ir.ramtung.tinyme.domain.entity.MatchResult;
import ir.ramtung.tinyme.domain.entity.MatchingOutcome;
import ir.ramtung.tinyme.domain.entity.Security;
import ir.ramtung.tinyme.domain.entity.StopLimitOrder;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.event.OrderActivatedEvent;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import org.springframework.stereotype.Component;

@Component
public class OrderActivatedPublisher implements Publisher {
    @Override
    public void enterOrderRqHandled(EnterOrderRq enterOrderRq, MatchResult matchResult, Security security, EventPublisher eventPublisher) {
        if (matchResult.outcome() != MatchingOutcome.NOT_ACTIVATABLE && enterOrderRq.getStopPrice() > 0)
            eventPublisher.publish(new OrderActivatedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId()));
    }

    @Override
    public void queuedOrderActivated(StopLimitOrder order, EventPublisher eventPublisher) {
        eventPublisher.publish(new OrderActivatedEvent(order.getRequestId(), order.getOrderId()));
    }
}
