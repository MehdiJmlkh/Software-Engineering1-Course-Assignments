package ir.ramtung.tinyme.domain.service.control;

import ir.ramtung.tinyme.domain.entity.MatchResult;
import ir.ramtung.tinyme.domain.entity.MatchingOutcome;
import ir.ramtung.tinyme.domain.entity.Order;
import ir.ramtung.tinyme.domain.entity.Trade;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.List;

@Component
public class MatchingControlList {
    @Autowired
    private List<MatchingControl> controlList;

    public MatchingOutcome canStartMatching(Order order) {
        for (MatchingControl control : controlList) {
            MatchingOutcome outcome = control.canStartMatching(order);
            if (outcome != MatchingOutcome.OK)
                return outcome;
        }
        return MatchingOutcome.OK;
    }

    public void matchingStarted(Order order) {
        for (MatchingControl control : controlList) {
            control.matchingStarted(order);
        }
    }

    public MatchingOutcome canContinueMatching(Order order) {
        for (MatchingControl control : controlList) {
            MatchingOutcome outcome = control.canContinueMatching(order);
            if (outcome != MatchingOutcome.OK) {
                return outcome;
            }
        }
        return MatchingOutcome.OK;
    }

    public MatchingOutcome canAcceptMatching(Order order, MatchResult result) {
        for (MatchingControl control : controlList) {
            MatchingOutcome outcome = control.canAcceptMatching(order, result);
            if (outcome != MatchingOutcome.OK) {
                return outcome;
            }
        }
        return MatchingOutcome.OK;
    }

    public void matchingAccepted(Order order, MatchResult result) {
        for (MatchingControl control : controlList) {
            control.matchingAccepted(order, result);
        }
    }

    public MatchingOutcome canTrade(Order newOrder, Trade trade) {
        for (MatchingControl control : controlList) {
            MatchingOutcome outcome = control.canTrade(newOrder, trade);
            if (outcome != MatchingOutcome.OK)
                return outcome;
        }
        return MatchingOutcome.OK;
    }

    public void tradeAccepted(Order newOrder, Trade trade) {
        for (MatchingControl control : controlList) {
            control.tradeAccepted(newOrder, trade);
        }
    }

    public void marketOpenned(Order order) {
        for (MatchingControl control : controlList)
            control.marketOpenned(order);
    }

    public void rollbackTrades(Order newOrder, LinkedList<Trade> trades) {
        for (MatchingControl control2 : controlList) {
            control2.rollbackTrades(newOrder, trades);
        }
    }
}
