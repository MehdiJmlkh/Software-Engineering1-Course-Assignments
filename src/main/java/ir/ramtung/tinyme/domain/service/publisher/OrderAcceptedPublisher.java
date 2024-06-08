package ir.ramtung.tinyme.domain.service.publisher;

import ir.ramtung.tinyme.domain.entity.MatchResult;
import ir.ramtung.tinyme.domain.entity.Security;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.event.OrderAcceptedEvent;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.messaging.request.OrderEntryType;
import org.springframework.stereotype.Component;

@Component
public class OrderAcceptedPublisher implements Publisher {
    @Override
    public void enterOrderRqHandled(EnterOrderRq enterOrderRq, MatchResult matchResult, Security security, EventPublisher eventPublisher) {
        if (enterOrderRq.getRequestType() == OrderEntryType.NEW_ORDER)
            eventPublisher.publish(new OrderAcceptedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId()));
    }
}
