package ir.ramtung.tinyme.domain.service.publisher;

import ir.ramtung.tinyme.domain.entity.MatchResult;
import ir.ramtung.tinyme.domain.entity.MatchingOutcome;
import ir.ramtung.tinyme.domain.entity.Security;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.event.OpeningPriceEvent;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.messaging.request.MatchingState;
import org.springframework.stereotype.Component;

@Component
public class OpeningPricePublisher implements Publisher {
    @Override
    public void enterOrderRqHandled(EnterOrderRq enterOrderRq, MatchResult matchResult, Security security, EventPublisher eventPublisher) {
        if(matchResult.outcome() == MatchingOutcome.QUEUED_DURING_AUCTION_STATE)
            eventPublisher.publish(new OpeningPriceEvent(enterOrderRq.getSecurityIsin(), security.getOpeningPrice(), security.tradableQuantity()));
    }

    @Override
    public void deleteOrderRqHandled(DeleteOrderRq deleteOrderRq, Security security, EventPublisher eventPublisher) {
        if (security.getMatchingState() == MatchingState.AUCTION)
            eventPublisher.publish(new OpeningPriceEvent(deleteOrderRq.getSecurityIsin(), security.getOpeningPrice(), security.tradableQuantity()));
    }
}
