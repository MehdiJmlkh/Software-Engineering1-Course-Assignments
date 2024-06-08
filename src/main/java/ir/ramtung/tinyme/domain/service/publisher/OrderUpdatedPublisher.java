package ir.ramtung.tinyme.domain.service.publisher;

import ir.ramtung.tinyme.domain.entity.MatchResult;
import ir.ramtung.tinyme.domain.entity.Security;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.event.OrderUpdatedEvent;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.messaging.request.OrderEntryType;
import org.springframework.stereotype.Component;

@Component
public class OrderUpdatedPublisher implements Publisher {
    @Override
    public void enterOrderRqHandled(EnterOrderRq enterOrderRq, MatchResult matchResult, Security security, EventPublisher eventPublisher) {
        if(enterOrderRq.getRequestType() == OrderEntryType.UPDATE_ORDER)
            eventPublisher.publish(new OrderUpdatedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId()));
    }
}
