/*******************************************************************************
 * Copyright (c) quickfixengine.org  All rights reserved. 
 *
 * This file is part of the QuickFIX FIX Engine 
 *
 * This file may be distributed under the terms of the quickfixengine.org 
 * license as defined by quickfixengine.org and appearing in the file 
 * LICENSE included in the packaging of this file. 
 *
 * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING 
 * THE WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A 
 * PARTICULAR PURPOSE. 
 *
 * See http://www.quickfixengine.org/LICENSE for licensing information. 
 *
 * Contact ask@quickfixengine.org if any conditions of this licensing 
 * are not clear to you.
 ******************************************************************************/

package fixsession.executor;

import fixsession.cli.CliServer;
import fixsession.shared.SessionInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import quickfix.ConfigError;
import quickfix.DataDictionaryProvider;
import quickfix.DoNotSend;
import quickfix.FieldConvertError;
import quickfix.FieldNotFound;
import quickfix.FixVersions;
import quickfix.IncorrectDataFormat;
import quickfix.IncorrectTagValue;
import quickfix.LogUtil;
import quickfix.Message;
import quickfix.MessageUtils;
import quickfix.RejectLogon;
import quickfix.Session;
import quickfix.SessionID;
import quickfix.SessionNotFound;
import quickfix.SessionSettings;
import quickfix.UnsupportedMessageType;
import fixsession.cli.Mutator;
import quickfix.field.ApplVerID;
import quickfix.field.AvgPx;
import quickfix.field.CumQty;
import quickfix.field.ExecID;
import quickfix.field.ExecTransType;
import quickfix.field.ExecType;
import quickfix.field.LastPx;
import quickfix.field.LastQty;
import quickfix.field.LastShares;
import quickfix.field.LeavesQty;
import quickfix.field.OrdStatus;
import quickfix.field.OrdType;
import quickfix.field.OrderID;
import quickfix.field.OrderQty;
import quickfix.field.Price;
import quickfix.field.Side;
import quickfix.field.Symbol;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class Application extends quickfix.MessageCracker implements quickfix.ApplicationExtended {
    private static final Logger LOG = LogManager.getLogger();
    private static final String DEFAULT_MARKET_PRICE_KEY = "DefaultMarketPrice";
    private static final String ALWAYS_FILL_LIMIT_KEY = "AlwaysFillLimitOrders";
    private static final String VALID_ORDER_TYPES_KEY = "ValidOrderTypes";
    public static final SessionID DROP_SESSION = new SessionID("FIX.4.2", "EXEC", "DROPCOPY");


    private final boolean alwaysFillLimitOrders;
    private final HashSet<String> validOrderTypes = new HashSet<String>();
    private MarketDataProvider marketDataProvider;
    private int m_orderID = 0;
    private int m_execID = 0;

    public Application(SessionSettings settings) throws ConfigError, FieldConvertError {
        initializeValidOrderTypes(settings);
        initializeMarketDataProvider(settings);

        if (settings.isSetting(ALWAYS_FILL_LIMIT_KEY)) {
            alwaysFillLimitOrders = settings.getBool(ALWAYS_FILL_LIMIT_KEY);
        } else {
            alwaysFillLimitOrders = false;
        }
    }

    private void initializeMarketDataProvider(SessionSettings settings) throws ConfigError, FieldConvertError {
        if (settings.isSetting(DEFAULT_MARKET_PRICE_KEY)) {
            if (marketDataProvider == null) {
                final double defaultMarketPrice = settings.getDouble(DEFAULT_MARKET_PRICE_KEY);
                marketDataProvider = new MarketDataProvider() {
                    public double getAsk(String symbol) {
                        return defaultMarketPrice;
                    }

                    public double getBid(String symbol) {
                        return defaultMarketPrice;
                    }
                };
            } else {
                LOG.warn("Ignoring {} since provider is already defined.", DEFAULT_MARKET_PRICE_KEY);
            }
        }
    }

    private void initializeValidOrderTypes(SessionSettings settings) throws ConfigError, FieldConvertError {
        if (settings.isSetting(VALID_ORDER_TYPES_KEY)) {
            List<String> orderTypes = Arrays
                    .asList(settings.getString(VALID_ORDER_TYPES_KEY).trim().split("\\s*,\\s*"));
            validOrderTypes.addAll(orderTypes);
        } else {
            validOrderTypes.add(OrdType.LIMIT + "");
        }
    }

    public void onCreate(SessionID sessionID) {
        Session.lookupSession(sessionID).getLog().onEvent("Valid order types: " + validOrderTypes);
        LOG.debug("{} has been created", sessionID);

    }

    public void onLogon(SessionID sessionID) {
        LOG.debug("{} has logged on", sessionID);
        CliServer.INST.addQ(sessionID.toString() + " has logged on");
    }

    public void onLogout(SessionID sessionID) {
        LOG.debug("{} has logged out", sessionID);
        CliServer.INST.addQ(sessionID.toString() + " has logged out");
    }

    public void toAdmin(quickfix.Message message, SessionID sessionID) {
    }

    public void toApp(quickfix.Message message, SessionID sessionID) throws DoNotSend {
        Mutator.INST.process(message);
    }

    public void fromAdmin(quickfix.Message message, SessionID sessionID) throws FieldNotFound, IncorrectDataFormat,
            IncorrectTagValue, RejectLogon {
        if (!SessionInfo.INST.canLogon(sessionID))
            throw new RejectLogon("not allowed");
    }

    public void fromApp(quickfix.Message message, SessionID sessionID) throws FieldNotFound, IncorrectDataFormat,
            IncorrectTagValue, UnsupportedMessageType {
        crack(message, sessionID);
    }

    public void onMessage(quickfix.fix40.NewOrderSingle order, SessionID sessionID) throws FieldNotFound,
            UnsupportedMessageType, IncorrectTagValue {
        try {
            validateOrder(order);

            OrderQty orderQty = order.getOrderQty();

            Price price = getPrice(order);

            quickfix.fix40.ExecutionReport accept = new quickfix.fix40.ExecutionReport(genOrderID(), genExecID(),
                    new ExecTransType(ExecTransType.NEW), new OrdStatus(OrdStatus.NEW), order.getSymbol(), order.getSide(),
                    orderQty, new LastShares(0), new LastPx(0), new CumQty(0), new AvgPx(0));

            accept.set(order.getClOrdID());
            sendMessage(sessionID, accept);

            if (isOrderExecutable(order, price)) {
                quickfix.fix40.ExecutionReport fill = new quickfix.fix40.ExecutionReport(genOrderID(), genExecID(),
                        new ExecTransType(ExecTransType.NEW), new OrdStatus(OrdStatus.FILLED), order.getSymbol(), order
                        .getSide(), orderQty, new LastShares(orderQty.getValue()), new LastPx(price.getValue()),
                        new CumQty(orderQty.getValue()), new AvgPx(price.getValue()));

                fill.set(order.getClOrdID());

                sendMessage(sessionID, fill);
            }
        } catch (RuntimeException e) {
            LogUtil.logThrowable(sessionID, e.getMessage(), e);
        }
    }

    private boolean isOrderExecutable(Message order, Price price) throws FieldNotFound {
        if (order.getChar(OrdType.FIELD) == OrdType.LIMIT) {
            BigDecimal limitPrice = new BigDecimal(order.getString(Price.FIELD));
            char side = order.getChar(Side.FIELD);
            BigDecimal thePrice = new BigDecimal("" + price.getValue());

            return (side == Side.BUY && thePrice.compareTo(limitPrice) <= 0)
                    || ((side == Side.SELL || side == Side.SELL_SHORT) && thePrice.compareTo(limitPrice) >= 0);
        }
        return true;
    }

    private Price getPrice(Message message) throws FieldNotFound {
        Price price;
        if (message.getChar(OrdType.FIELD) == OrdType.LIMIT && alwaysFillLimitOrders) {
            price = new Price(message.getDouble(Price.FIELD));
        } else {
            if (marketDataProvider == null) {
                throw new RuntimeException("No market data provider specified for market order");
            }
            char side = message.getChar(Side.FIELD);
            if (side == Side.BUY) {
                price = new Price(marketDataProvider.getAsk(message.getString(Symbol.FIELD)));
            } else if (side == Side.SELL || side == Side.SELL_SHORT) {
                price = new Price(marketDataProvider.getBid(message.getString(Symbol.FIELD)));
            } else {
                throw new RuntimeException("Invalid order side: " + side);
            }
        }
        return price;
    }

    private void sendMessage(SessionID sessionID, Message message) {
        try {
            Session session = Session.lookupSession(sessionID);
            if (session == null) {
                throw new SessionNotFound(sessionID.toString());
            }

            DataDictionaryProvider dataDictionaryProvider = session.getDataDictionaryProvider();
            if (dataDictionaryProvider != null) {
                try {
                    dataDictionaryProvider.getApplicationDataDictionary(
                            getApplVerID(session, message)).validate(message, true);
                } catch (Exception e) {
                    LogUtil.logThrowable(sessionID, "Outgoing message failed validation: "
                            + e.getMessage(), e);
                    return;
                }
            }

            session.send(message);
            Session drcp = Session.lookupSession(DROP_SESSION);
            if(drcp!=null)
                drcp.send(message);
        } catch (SessionNotFound e) {
            LOG.error(e.getMessage(), e);
        }
    }

    private ApplVerID getApplVerID(Session session, Message message) {
        String beginString = session.getSessionID().getBeginString();
        if (FixVersions.BEGINSTRING_FIXT11.equals(beginString)) {
            return new ApplVerID(ApplVerID.FIX50);
        } else {
            return MessageUtils.toApplVerID(beginString);
        }
    }

    public void onMessage(quickfix.fix41.NewOrderSingle order, SessionID sessionID) throws FieldNotFound,
            UnsupportedMessageType, IncorrectTagValue {
        try {
            validateOrder(order);

            OrderQty orderQty = order.getOrderQty();
            Price price = getPrice(order);

            quickfix.fix41.ExecutionReport accept = new quickfix.fix41.ExecutionReport(genOrderID(), genExecID(),
                    new ExecTransType(ExecTransType.NEW), new ExecType(ExecType.FILL), new OrdStatus(OrdStatus.NEW), order
                    .getSymbol(), order.getSide(), orderQty, new LastShares(0), new LastPx(0), new LeavesQty(0),
                    new CumQty(0), new AvgPx(0));

            accept.set(order.getClOrdID());
            sendMessage(sessionID, accept);

            if (isOrderExecutable(order, price)) {
                quickfix.fix41.ExecutionReport executionReport = new quickfix.fix41.ExecutionReport(genOrderID(),
                        genExecID(), new ExecTransType(ExecTransType.NEW), new ExecType(ExecType.FILL), new OrdStatus(
                        OrdStatus.FILLED), order.getSymbol(), order.getSide(), orderQty, new LastShares(orderQty
                        .getValue()), new LastPx(price.getValue()), new LeavesQty(0), new CumQty(orderQty
                        .getValue()), new AvgPx(price.getValue()));

                executionReport.set(order.getClOrdID());

                sendMessage(sessionID, executionReport);
            }
        } catch (RuntimeException e) {
            LogUtil.logThrowable(sessionID, e.getMessage(), e);
        }
    }

    public void onMessage(quickfix.fix42.NewOrderSingle order, SessionID sessionID) throws FieldNotFound,
            UnsupportedMessageType, IncorrectTagValue {
        try {
            validateOrder(order);

            OrderQty orderQty = order.getOrderQty();
            Price price = getPrice(order);

            quickfix.fix42.ExecutionReport accept = new quickfix.fix42.ExecutionReport(genOrderID(), genExecID(),
                    new ExecTransType(ExecTransType.NEW), new ExecType(ExecType.FILL), new OrdStatus(OrdStatus.NEW), order
                    .getSymbol(), order.getSide(), new LeavesQty(0), new CumQty(0), new AvgPx(0));

            accept.set(order.getClOrdID());
            sendMessage(sessionID, accept);

            if (isOrderExecutable(order, price)) {
                quickfix.fix42.ExecutionReport executionReport = new quickfix.fix42.ExecutionReport(genOrderID(),
                        genExecID(), new ExecTransType(ExecTransType.NEW), new ExecType(ExecType.FILL), new OrdStatus(
                        OrdStatus.FILLED), order.getSymbol(), order.getSide(), new LeavesQty(0), new CumQty(
                        orderQty.getValue()), new AvgPx(price.getValue()));

                executionReport.set(order.getClOrdID());
                executionReport.set(orderQty);
                executionReport.set(new LastShares(orderQty.getValue()));
                executionReport.set(new LastPx(price.getValue()));

                sendMessage(sessionID, executionReport);

            }
        } catch (RuntimeException e) {
            LogUtil.logThrowable(sessionID, e.getMessage(), e);
        }
    }

    private void validateOrder(Message order) throws IncorrectTagValue, FieldNotFound {
        OrdType ordType = new OrdType(order.getChar(OrdType.FIELD));
        if (!validOrderTypes.contains(Character.toString(ordType.getValue()))) {
            LOG.error("Order type not in ValidOrderTypes setting");
            throw new IncorrectTagValue(ordType.getField());
        }
        if (ordType.getValue() == OrdType.MARKET && marketDataProvider == null) {
            LOG.error("DefaultMarketPrice setting not specified for market order");
            throw new IncorrectTagValue(ordType.getField());
        }
    }

    public void onMessage(quickfix.fix43.NewOrderSingle order, SessionID sessionID) throws FieldNotFound,
            UnsupportedMessageType, IncorrectTagValue {
        try {
            validateOrder(order);

            OrderQty orderQty = order.getOrderQty();
            Price price = getPrice(order);

            quickfix.fix43.ExecutionReport accept = new quickfix.fix43.ExecutionReport(
                    genOrderID(), genExecID(), new ExecType(ExecType.FILL), new OrdStatus(
                    OrdStatus.NEW), order.getSide(), new LeavesQty(order.getOrderQty()
                    .getValue()), new CumQty(0), new AvgPx(0));

            accept.set(order.getClOrdID());
            accept.set(order.getSymbol());
            sendMessage(sessionID, accept);

            if (isOrderExecutable(order, price)) {
                quickfix.fix43.ExecutionReport executionReport = new quickfix.fix43.ExecutionReport(genOrderID(),
                        genExecID(), new ExecType(ExecType.FILL), new OrdStatus(OrdStatus.FILLED), order.getSide(),
                        new LeavesQty(0), new CumQty(orderQty.getValue()), new AvgPx(price.getValue()));

                executionReport.set(order.getClOrdID());
                executionReport.set(order.getSymbol());
                executionReport.set(orderQty);
                executionReport.set(new LastQty(orderQty.getValue()));
                executionReport.set(new LastPx(price.getValue()));

                sendMessage(sessionID, executionReport);
            }
        } catch (RuntimeException e) {
            LogUtil.logThrowable(sessionID, e.getMessage(), e);
        }
    }

    public void onMessage(quickfix.fix44.NewOrderSingle order, SessionID sessionID) throws FieldNotFound,
            UnsupportedMessageType, IncorrectTagValue {
        try {
            validateOrder(order);

            OrderQty orderQty = order.getOrderQty();
            Price price = getPrice(order);

            quickfix.fix44.ExecutionReport accept = new quickfix.fix44.ExecutionReport(
                    genOrderID(), genExecID(), new ExecType(ExecType.FILL), new OrdStatus(
                    OrdStatus.NEW), order.getSide(), new LeavesQty(order.getOrderQty()
                    .getValue()), new CumQty(0), new AvgPx(0));

            accept.set(order.getClOrdID());
            accept.set(order.getSymbol());
            sendMessage(sessionID, accept);

            if (isOrderExecutable(order, price)) {
                quickfix.fix44.ExecutionReport executionReport = new quickfix.fix44.ExecutionReport(genOrderID(),
                        genExecID(), new ExecType(ExecType.FILL), new OrdStatus(OrdStatus.FILLED), order.getSide(),
                        new LeavesQty(0), new CumQty(orderQty.getValue()), new AvgPx(price.getValue()));

                executionReport.set(order.getClOrdID());
                executionReport.set(order.getSymbol());
                executionReport.set(orderQty);
                executionReport.set(new LastQty(orderQty.getValue()));
                executionReport.set(new LastPx(price.getValue()));

                sendMessage(sessionID, executionReport);
            }
        } catch (RuntimeException e) {
            LogUtil.logThrowable(sessionID, e.getMessage(), e);
        }
    }

    public void onMessage(quickfix.fix50.NewOrderSingle order, SessionID sessionID)
            throws FieldNotFound, UnsupportedMessageType, IncorrectTagValue {
        try {
            validateOrder(order);

            OrderQty orderQty = order.getOrderQty();
            Price price = getPrice(order);

            quickfix.fix50.ExecutionReport accept = new quickfix.fix50.ExecutionReport(
                    genOrderID(), genExecID(), new ExecType(ExecType.FILL), new OrdStatus(
                    OrdStatus.NEW), order.getSide(), new LeavesQty(order.getOrderQty()
                    .getValue()), new CumQty(0));

            accept.set(order.getClOrdID());
            accept.set(order.getSymbol());
            sendMessage(sessionID, accept);

            if (isOrderExecutable(order, price)) {
                quickfix.fix50.ExecutionReport executionReport = new quickfix.fix50.ExecutionReport(
                        genOrderID(), genExecID(), new ExecType(ExecType.FILL), new OrdStatus(
                        OrdStatus.FILLED), order.getSide(), new LeavesQty(0), new CumQty(
                        orderQty.getValue()));

                executionReport.set(order.getClOrdID());
                executionReport.set(order.getSymbol());
                executionReport.set(orderQty);
                executionReport.set(new LastQty(orderQty.getValue()));
                executionReport.set(new LastPx(price.getValue()));
                executionReport.set(new AvgPx(price.getValue()));

                sendMessage(sessionID, executionReport);
            }
        } catch (RuntimeException e) {
            LogUtil.logThrowable(sessionID, e.getMessage(), e);
        }
    }

    public OrderID genOrderID() {
        return new OrderID(Integer.valueOf(++m_orderID).toString());
    }

    public ExecID genExecID() {
        return new ExecID(Integer.valueOf(++m_execID).toString());
    }

    /**
     * Allows a custom market data provider to be specified.
     *
     * @param marketDataProvider
     */
    public void setMarketDataProvider(MarketDataProvider marketDataProvider) {
        this.marketDataProvider = marketDataProvider;
    }

    @Override
    public boolean canLogon(SessionID sessionID) {

        boolean ret = SessionInfo.INST.canLogon(sessionID);
        LOG.debug("{} is allowed = {}", sessionID, ret);
        return ret;
    }

    @Override
    public void onBeforeSessionReset(SessionID sessionID) {

    }
}
