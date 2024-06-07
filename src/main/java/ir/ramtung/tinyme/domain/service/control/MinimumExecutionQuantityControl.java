package ir.ramtung.tinyme.domain.service.control;

import ir.ramtung.tinyme.domain.entity.MatchResult;
import ir.ramtung.tinyme.domain.entity.MatchingOutcome;
import ir.ramtung.tinyme.domain.entity.Order;
import org.springframework.stereotype.Component;

@Component
public class MinimumExecutionQuantityControl implements MatchingControl {
    public MatchingOutcome canAcceptMatching(Order order, MatchResult result) {
        if (order.minimumExecutionQuantitySatisfied() || !order.isNew())
            return MatchingOutcome.OK;
        else return MatchingOutcome.MINIMUM_QUANTITY_NOT_SATISFIED;
    }
}
