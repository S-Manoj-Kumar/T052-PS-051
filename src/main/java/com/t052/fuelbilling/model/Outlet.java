
package com.t052.fuelbilling.model;

public class Outlet {

    private String outletId;
    private String outletName;
    private String city;
    private String state;
    private String status;

    public Outlet(
            String outletId,
            String outletName,
            String city,
            String state,
            String status) {

        this.outletId = outletId;
        this.outletName = outletName;
        this.city = city;
        this.state = state;
        this.status = status;
    }

    public String getOutletId() {
        return outletId;
    }

    public String getOutletName() {
        return outletName;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }

    public String getStatus() {
        return status;
    }
}
