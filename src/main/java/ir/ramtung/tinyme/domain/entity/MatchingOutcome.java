package ir.ramtung.tinyme.domain.entity;

public enum MatchingOutcome {
    OK,
    QUEUED_DURING_AUCTION_STATE,
    NOT_ACTIVATABLE,
    NOT_ENOUGH_CREDIT {
        @Override
        public boolean isError() {return true;}
    },
    NOT_ENOUGH_POSITIONS {
        @Override
        public boolean isError() {return true;}
    },
    MINIMUM_QUANTITY_NOT_SATISFIED {
        @Override
        public boolean isError() {return true;}
    },
    NOT_EQUAL_MINIMUM_EXECUTION_QUANTITY {
        @Override
        public boolean isError() {return true;}
    };

    public boolean isError() {return false;}
}
