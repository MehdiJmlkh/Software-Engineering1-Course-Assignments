package ir.ramtung.tinyme.messaging.request;


import lombok.AllArgsConstructor;

@AllArgsConstructor
public class ChangeMatchingStateRq {
    private String securityIsin;
    private MatchingState targetState;

}
