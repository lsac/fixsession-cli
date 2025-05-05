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

import java.util.Objects;

public class Execution {
    private static int nextID = 1;
    private String symbol = null;
    private int quantity = 0;
    private OrderSide side = OrderSide.BUY;
    private double price;
    private String ID = null;
    private String OrderId;
    private String exchangeID = null;

    public Execution() {
        ID = Integer.valueOf(nextID++).toString();
    }

    public Execution(String ID) {
        this.ID = ID;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getOrderId() {
        return OrderId;
    }

    public void setOrderId(String orderId) {
        OrderId = orderId;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public OrderSide getSide() {
        return side;
    }

    public void setSide(OrderSide side) {
        this.side = side;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public String getID() {
        return ID;
    }

    public String getExchangeID() {
        return exchangeID;
    }

    public void setExchangeID(String exchangeID) {
        this.exchangeID = exchangeID;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Execution execution = (Execution) o;
        return quantity == execution.quantity && Double.compare(price, execution.price) == 0 && Objects.equals(symbol, execution.symbol) && Objects.equals(side, execution.side) && Objects.equals(ID, execution.ID) && Objects.equals(OrderId, execution.OrderId) && Objects.equals(exchangeID, execution.exchangeID);
    }

    @Override
    public int hashCode() {
        return Objects.hash(symbol, quantity, side, price, ID, OrderId, exchangeID);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Execution{");
        sb.append("symbol='").append(symbol).append('\'');
        sb.append(", quantity=").append(quantity);
        sb.append(", side=").append(side);
        sb.append(", price=").append(price);
        sb.append(", ID='").append(ID).append('\'');
        sb.append(", OrderId='").append(OrderId).append('\'');
        sb.append(", exchangeID='").append(exchangeID).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
