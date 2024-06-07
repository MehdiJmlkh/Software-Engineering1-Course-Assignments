package ir.ramtung.tinyme.domain.service.control;

import ir.ramtung.tinyme.domain.entity.*;
import org.springframework.stereotype.Component;

@Component
public class ActivationControl implements MatchingControl{
    @Override
    public void matchingStarted(Order order) {
        if (!orderIsActivatable(order))
            order.getSecurity().getOrderBook().enqueue(order);
    }

    @Override
    public MatchingOutcome canContinueMatching(Order order) {
        if (!orderIsActivatable(order))
            return MatchingOutcome.NOT_ACTIVATABLE;
        return MatchingOutcome.OK;
    }

    private boolean orderIsActivatable(Order order) {
        if (order instanceof StopLimitOrder stopLimitOrder)
            if (!stopLimitOrder.isActivatable(order.getSecurity().getMarketPrice()))
                return false;
        return true;
    }
}
