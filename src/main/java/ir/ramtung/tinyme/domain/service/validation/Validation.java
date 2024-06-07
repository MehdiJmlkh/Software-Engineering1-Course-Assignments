package ir.ramtung.tinyme.domain.service.validation;


import ir.ramtung.tinyme.messaging.request.ChangeMatchingStateRq;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.repository.Repositories;

import java.util.LinkedList;
import java.util.List;

public interface Validation {
    default List<String> validate(EnterOrderRq enterOrderRq, Repositories repositories) { return new LinkedList<>(); }
    default List<String> validate(DeleteOrderRq deleteOrderRq, Repositories repositories) { return new LinkedList<>(); }
    default List<String> validate(ChangeMatchingStateRq changeMatchingStateRq, Repositories repositories) { return new LinkedList<>(); }
}