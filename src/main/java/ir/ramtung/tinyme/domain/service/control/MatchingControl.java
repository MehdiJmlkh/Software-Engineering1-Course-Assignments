package ir.ramtung.tinyme.domain.service.control;

import ir.ramtung.tinyme.domain.entity.MatchResult;
import ir.ramtung.tinyme.domain.entity.MatchingOutcome;
import ir.ramtung.tinyme.domain.entity.Order;
import ir.ramtung.tinyme.domain.entity.Trade;

import java.util.LinkedList;

public interface MatchingControl {
    default MatchingOutcome canStartMatching(Order order) { return MatchingOutcome.OK; }
    default void matchingStarted(Order order) {}
    default MatchingOutcome canContinueMatching(Order order) {return MatchingOutcome.OK;}
    default MatchingOutcome canAcceptMatching(Order order, MatchResult result) { return MatchingOutcome.OK; }
    default void matchingAccepted(Order order, MatchResult result) {}

    default MatchingOutcome canTrade(Order newOrder, Trade trade) { return MatchingOutcome.OK; }
    default void tradeAccepted(Order newOrder, Trade trade) {}

    default void rollbackTrades(Order newOrder, LinkedList<Trade> trades) {}

    default void marketOpenned(Order order){}
}
