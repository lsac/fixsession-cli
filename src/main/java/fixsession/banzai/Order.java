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

package fixsession.banzai;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import quickfix.SessionID;

import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicLong;

public class Order implements Cloneable {
    private static final Logger LOG = LogManager.getLogger();
    private static int nextID = 1;
    private SessionID sessionID = null;
    private String symbol = null;
    private int quantity = 0;
    private int open = 0;
    private int executed = 0;
    private OrderSide side = OrderSide.BUY;
    private OrderType type = OrderType.MARKET;
    private OrderTIF tif = OrderTIF.DAY;
    private Double limit = null;
    private Double stop = null;
    private double avgPx = 0.0;
    private boolean rejected = false;
    private boolean canceled = false;
    private boolean isNew = true;
    private String message = null;
    private String ID = null;
    private String originalID = null;
    private long createTime;
    private long waitTime;
    private long sendTime;
    private long timediff;
    private long batch;

    public Order() {
        ID = generateID();
    }

    public Order(String ID) {
        this.ID = ID;
    }

    public Object clone() {
        try {
            Order order = (Order) super.clone();
            order.setOriginalID(getID());
            order.setID(order.generateID());

            order.setSide(getSide());
            order.setType(getType());
            order.setTIF(getTIF());
            order.setSymbol(getSymbol());
            order.setQuantity(getQuantity());
            order.setOpen(getOpen());
            order.setLimit(getLimit());
            order.setStop(getStop());
            order.setBatch(getBatch());
            order.setSessionID(getSessionID());
            order.setTimediff(order.getSendTime()-this.sendTime);

            return order;
        } catch (CloneNotSupportedException e) {
        }
        return null;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public long getWaitTime() {
        return waitTime;
    }

    public void setWaitTime(long waitTime) {
        this.waitTime = waitTime;
    }

    public long getBatch() {
        return batch;
    }

    public void setBatch(long batch) {
        this.batch = batch;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Order.class.getSimpleName() + "[", "]")
                .add("sessionID=" + sessionID)
                .add("symbol='" + symbol + "'")
                .add("quantity=" + quantity)
                .add("open=" + open)
                .add("executed=" + executed)
                .add("side=" + side)
                .add("type=" + type)
                .add("tif=" + tif)
                .add("limit=" + limit)
                .add("stop=" + stop)
                .add("avgPx=" + avgPx)
                .add("rejected=" + rejected)
                .add("canceled=" + canceled)
                .add("isNew=" + isNew)
                .add("message='" + message + "'")
                .add("ID='" + ID + "'")
                .add("originalID='" + originalID + "'")
                .add("createTime='" + createTime + "'")
                .add("waitTime='" + waitTime + "'")
                .add("sendtime='" + sendTime + "'")
                .add("timediff='" + timediff + "'")
                .add("batch='" + batch + "'")
                .toString();
    }

    public String generateID() {
        createTime=System.nanoTime();
        sendTime=createTime;

        return String.format("%05X_%X", nextID++ , createTime%10000000000000l);
    }

    public SessionID getSessionID() {
        return sessionID;
    }

    public void setSessionID(SessionID sessionID) {
        this.sessionID = sessionID;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public long getTimediff() {
        return timediff;
    }

    public void setTimediff(long timediff) {
        this.timediff = timediff;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public int getOpen() {
        return open;
    }

    public void setOpen(int open) {
        this.open = open;
    }

    public int getExecuted() {
        return executed;
    }

    public void setExecuted(int executed) {
        this.executed = executed;
    }

    public OrderSide getSide() {
        return side;
    }

    public void setSide(OrderSide side) {
        this.side = side;
    }

    public OrderType getType() {
        return type;
    }

    public void setType(OrderType type) {
        this.type = type;
    }

    public OrderTIF getTIF() {
        return tif;
    }

    public void setTIF(OrderTIF tif) {
        this.tif = tif;
    }

    public long getSendTime() {
        return sendTime;
    }

    public void setSendTime(long sendTime) {
        this.sendTime = sendTime;
    }
    public void setSendTimex(long sendTime, long last) {
        this.sendTime = sendTime;
        this.timediff=sendTime-last;
        LOG.debug("{} with last {}", this, last);
    }

    public Double getLimit() {
        return limit;
    }

    public void setLimit(Double limit) {
        this.limit = limit;
    }

    public void setLimit(String limit) {
        if (limit == null || limit.equals("")) {
            this.limit = null;
        } else {
            this.limit = new Double(limit);
        }
    }

    public Double getStop() {
        return stop;
    }

    public void setStop(Double stop) {
        this.stop = stop;
    }

    public void setStop(String stop) {
        if (stop == null || stop.equals("")) {
            this.stop = null;
        } else {
            this.stop = new Double(stop);
        }
    }

    public double getAvgPx() {
        return avgPx;
    }

    public void setAvgPx(double avgPx) {
        this.avgPx = avgPx;
    }

    public boolean getRejected() {
        return rejected;
    }

    public void setRejected(boolean rejected) {
        this.rejected = rejected;
    }

    public boolean getCanceled() {
        return canceled;
    }

    public void setCanceled(boolean canceled) {
        this.canceled = canceled;
    }

    public boolean isNew() {
        return isNew;
    }

    public void setNew(boolean isNew) {
        this.isNew = isNew;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getID() {
        return ID;
    }

    public void setID(String ID) {
        this.ID = ID;
    }

    public String getOriginalID() {
        return originalID;
    }

    public void setOriginalID(String originalID) {
        this.originalID = originalID;
    }
}
