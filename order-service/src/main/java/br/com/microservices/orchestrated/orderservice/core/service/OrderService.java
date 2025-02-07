package br.com.microservices.orchestrated.orderservice.core.service;

import br.com.microservices.orchestrated.orderservice.core.document.Event;
import br.com.microservices.orchestrated.orderservice.core.document.Order;
import br.com.microservices.orchestrated.orderservice.core.dto.OrderRequest;
import br.com.microservices.orchestrated.orderservice.core.producer.SagaProducer;
import br.com.microservices.orchestrated.orderservice.core.repository.OrderRepository;
import br.com.microservices.orchestrated.orderservice.core.utils.JsonUtil;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class OrderService {

    private static final String TRANSACTION_ID_PATTERM = "%s_%s";

    private final EventService eventService;
    private final SagaProducer sagaProducer;
    private final JsonUtil jsonUtil;
    private final OrderRepository orderRepository;

    public OrderService(EventService eventService, SagaProducer sagaProducer, JsonUtil jsonUtil, OrderRepository orderRepository) {
        this.eventService = eventService;
        this.sagaProducer = sagaProducer;
        this.jsonUtil = jsonUtil;
        this.orderRepository = orderRepository;
    }

    public Order createOrder(OrderRequest orderRequest){
        var order = new Order(
            orderRequest.getProducts(),
            LocalDateTime.now(),
            String.format(TRANSACTION_ID_PATTERM, Instant.now().toEpochMilli(), UUID.randomUUID())
        );
        orderRepository.save(order);

        sagaProducer.sendEvent(jsonUtil.toJson(createPayload(order)));

        return order;
    }

    private Event createPayload(Order order){
        var event = new Event(
                order.getTransactionId(),
                LocalDateTime.now(),
                order.getId(),
                order
        );

        eventService.save(event);

        return event;
    }
}
