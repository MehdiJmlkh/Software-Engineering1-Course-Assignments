package ir.ramtung.tinyme.domain.service.publisher;

import ir.ramtung.tinyme.domain.entity.Security;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.event.OrderDeletedEvent;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import org.springframework.stereotype.Component;

@Component
public class OrderDeletedPublisher implements Publisher {
    @Override
    public void deleteOrderRqHandled(DeleteOrderRq deleteOrderRq, Security security, EventPublisher eventPublisher) {
        eventPublisher.publish(new OrderDeletedEvent(deleteOrderRq.getRequestId(), deleteOrderRq.getOrderId()));
    }
}
