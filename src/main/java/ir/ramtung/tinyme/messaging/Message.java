package ir.ramtung.tinyme.messaging;

import org.apache.commons.beanutils.PropertyUtilsBean;

import java.util.Stack;

public class Message {
    public static final String INVALID_ORDER_ID = "Invalid order ID";
    public static final String ORDER_QUANTITY_NOT_POSITIVE = "Order quantity is not-positive";
    public static final String ORDER_PRICE_NOT_POSITIVE = "Order price is not-positive";
    public static final String ORDER_MINIMUM_EXECUTION_QUANTITY_NOT_POSITIVE = "Order minimum execution quantity is not-positive";
    public static final String UNKNOWN_SECURITY_ISIN = "Unknown security ISIN";
    public static final String ORDER_ID_NOT_FOUND = "Order ID not found in the order book";
    public static final String INVALID_PEAK_SIZE = "Iceberg order peak size is out of range";
    public static final String CANNOT_SPECIFY_PEAK_SIZE_FOR_A_NON_ICEBERG_ORDER = "Cannot specify peak size for a non-iceberg order";
    public static final String UNKNOWN_BROKER_ID = "Unknown broker ID";
    public static final String UNKNOWN_SHAREHOLDER_ID = "Unknown shareholder ID";
    public static final String BUYER_HAS_NOT_ENOUGH_CREDIT = "Buyer has not enough credit";
    public static final String QUANTITY_NOT_MULTIPLE_OF_LOT_SIZE = "Quantity is not a multiple of security lot size";
    public static final String PRICE_NOT_MULTIPLE_OF_TICK_SIZE = "Price is not a multiple of security tick size";
    public static final String MINIMUM_EXECUTION_QUANTITY_NOT_LESS_THAN_OR_EQUAL_TO_QUANTITY = "Minimum execution quantity is not less than or equal to quantity";
    public static final String SELLER_HAS_NOT_ENOUGH_POSITIONS = "Seller has not enough positions";
    public static final String ORDER_HAS_NOT_EXECUTED_MINIMUM_EXECUTION_QUANTITY = "Order has not executed minimum execution quantity";
    public static final String MINIMUM_EXECUTION_QUANTITY_OF_UPDATE_ORDER_HAS_CHANGED = "Minimum execution quantity of update order has changed";
    public static final String CANNOT_SPECIFY_MINIMUM_EXECUTION_QUANTITY_FOR_A_STOP_LIMIT_ORDER = "Cannot specify minimum execution quantity for a stop limit order";
    public static final String STOP_LIMIT_ORDER_CAN_NOT_BE_ICEBERG_ORDER = "Stop limit order can not be iceberg order";
    public static final String CANNOT_SPECIFY_STOP_PRICE_FOR_A_ACTIVATED_ORDER = "Cannot specify stop price for a activated order";
    public static final String CANNOT_SPECIFY_MINIMUM_EXECUTION_QUANTITY_IN_THE_AUCTION_STATE = "Cannot specify minimum execution quantity for an order in the auction state";
    public static final String CANNOT_SUBMIT_OR_UPDATE_STOP_LIMIT_ORDER_IN_THE_AUCTION_STATE = "Cannot submit or update stop limit order in the auction state";
    public static final String CANNOT_DELETE_STOP_LIMIT_ORDER_IN_THE_AUCTION_STATE = "Cannot delete stop limit order in the auction state";
}
