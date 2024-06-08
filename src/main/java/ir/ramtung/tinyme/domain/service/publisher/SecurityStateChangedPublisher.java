package ir.ramtung.tinyme.domain.service.publisher;

import ir.ramtung.tinyme.domain.entity.Trade;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.event.SecurityStateChangedEvent;
import ir.ramtung.tinyme.messaging.request.ChangeMatchingStateRq;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SecurityStateChangedPublisher implements Publisher {
    @Override
    public void changeMatchingStateRqHandled(ChangeMatchingStateRq changeMatchingStateRq, List<Trade> trades, EventPublisher eventPublisher) {
        eventPublisher.publish(new SecurityStateChangedEvent(changeMatchingStateRq.getSecurityIsin(), changeMatchingStateRq.getTargetState()));
    }
}
