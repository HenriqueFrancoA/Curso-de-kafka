package br.com.microservices.orchestrated.productvalidationservice.core.service;

import br.com.microservices.orchestrated.productvalidationservice.config.exception.ValidationException;
import br.com.microservices.orchestrated.productvalidationservice.core.dto.Event;
import br.com.microservices.orchestrated.productvalidationservice.core.dto.History;
import br.com.microservices.orchestrated.productvalidationservice.core.dto.OrderProducts;
import br.com.microservices.orchestrated.productvalidationservice.core.enums.ESagaStatus;
import br.com.microservices.orchestrated.productvalidationservice.core.model.Validation;
import br.com.microservices.orchestrated.productvalidationservice.core.producer.KafkaProducer;
import br.com.microservices.orchestrated.productvalidationservice.core.repository.ProductRepository;
import br.com.microservices.orchestrated.productvalidationservice.core.repository.ValidationRepository;
import br.com.microservices.orchestrated.productvalidationservice.core.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@Slf4j
public class ProductValidationService {

    private static final String CURRENT_SOURCE = "PRODUCT_VALIDATION_SERVICE";
    private static final Logger log = LoggerFactory.getLogger(ProductValidationService.class);

    private final JsonUtil jsonUtil;

    private final KafkaProducer kafkaProducer;

    private final ProductRepository productRepository;

    private final ValidationRepository validationRepository;


    public ProductValidationService(JsonUtil jsonUtil, KafkaProducer kafkaProducer, ProductRepository productRepository, ValidationRepository validationRepository) {
        this.jsonUtil = jsonUtil;
        this.kafkaProducer = kafkaProducer;
        this.productRepository = productRepository;
        this.validationRepository = validationRepository;
    }

    public void validateExistingProducts(Event event){
        try {
            checkCurrentValidation(event);
            createValidation(event, true);
            handleSuccess(event);
        } catch (Exception ex) {
            log.error("Error trying to validate products: ", ex);
            handleFailCurrentNotExecuted(event, ex.getMessage());
        }
        kafkaProducer.sendEvent(jsonUtil.toJson(event));
    }

    private void validateProductsInformed(Event event){
        if(event.getPayload() == null || event.getPayload().getProducts().isEmpty())
            throw new ValidationException("Product list is empty!");


        if(event.getPayload().getId().isEmpty() || event.getPayload().getTransactionId().isEmpty())
            throw new ValidationException("OrderId and TransactionID must be informed!");

    }

    private void checkCurrentValidation(Event event) {
        validateProductsInformed(event);

        if(validationRepository.existsByOrderIdAndTransactionId(event.getOrderId(), event.getTransactionId()))
            throw new ValidationException("There's another transactionId for this validation.");


        event.getPayload().getProducts().forEach(product -> {
            validateProductInformed(product);
            validateExistingProduct(product.getProduct().getCode());
        });
    }

    private void validateProductInformed(OrderProducts product){
        if(product == null || product.getProduct().getCode().isEmpty())
            throw new ValidationException("Product must be informed!");

    }

    private void validateExistingProduct(String code) {
        if(!productRepository.existsByCode(code))
            throw new ValidationException("Product does not exists in database!");

    }


    private void createValidation(Event event, boolean success) {
        var validation = new Validation(
            event.getPayload().getId(),
            event.getTransactionId(),
            success
        );
        validationRepository.save(validation);
    }

    private void handleSuccess(Event event) {
        event.setStatus(ESagaStatus.SUCCESS);
        event.setSource(CURRENT_SOURCE);

        addHistory(event, "Products are validated successfully!");
    }

    private void addHistory(Event event, String message){
        var history = new History(
                event.getSource(),
                event.getStatus(),
                message,
                LocalDateTime.now()
        );
        event.addToHistory(history);
    }

    private void handleFailCurrentNotExecuted(Event event, String message) {
        event.setStatus(ESagaStatus.ROLLBACK_PENDING);
        event.setSource(CURRENT_SOURCE);

        addHistory(event, "Fail to validate products: " + message);
    }

    public void rollbackEvent(Event event){
        changeValidationToFail(event);
        event.setStatus(ESagaStatus.FAIL);
        event.setSource(CURRENT_SOURCE);

        addHistory(event, "Rollback executed on product validation!");

        kafkaProducer.sendEvent(jsonUtil.toJson(event));
    }

    private void changeValidationToFail(Event event) {
        validationRepository.findByOrderIdAndTransactionId(event.getPayload().getId(), event.getTransactionId())
                .ifPresentOrElse(validation -> {
                    validation.setSuccess(false);
                    validationRepository.save(validation);
                }, () -> createValidation(event, false));
    }
}
