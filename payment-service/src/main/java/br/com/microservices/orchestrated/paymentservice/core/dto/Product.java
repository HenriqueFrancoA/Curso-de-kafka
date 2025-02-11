package br.com.microservices.orchestrated.paymentservice.core.dto;

public class Product {

    private String code;
    private double unitValue;

    public Product() {
    }

    public Product(String code, double unitValue) {
        this.code = code;
        this.unitValue = unitValue;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public double getUnitValue() {
        return unitValue;
    }

    public void setUnitValue(double unitValue) {
        this.unitValue = unitValue;
    }
}
