package br.com.microservices.orchestrated.orderservice.core.dto;

import br.com.microservices.orchestrated.orderservice.core.document.OrderProducts;

import java.util.List;

public class OrderRequest {

    private List<OrderProducts> products;

    public OrderRequest() {
    }

    public OrderRequest(List<OrderProducts> products) {
        this.products = products;
    }

    public List<OrderProducts> getProducts() {
        return products;
    }

    public void setProducts(List<OrderProducts> products) {
        this.products = products;
    }
}
