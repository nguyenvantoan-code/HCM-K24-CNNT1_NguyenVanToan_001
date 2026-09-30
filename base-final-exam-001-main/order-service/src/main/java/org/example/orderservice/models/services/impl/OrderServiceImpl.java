package org.example.orderservice.models.services.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.example.orderservice.exceptions.ProductNotFoundException;
import org.example.orderservice.models.constants.OrderStatus;
import org.example.orderservice.models.dto.requests.CreateOrderDetailRequest;
import org.example.orderservice.models.dto.requests.CreateOrderRequest;
import org.example.orderservice.models.dto.responses.OrderDetailResponse;
import org.example.orderservice.models.dto.responses.OrderResponse;
import org.example.orderservice.models.dto.responses.ProductResponse;
import org.example.orderservice.models.entities.Order;
import org.example.orderservice.models.entities.OrderDetail;
import org.example.orderservice.models.repositories.OrderDetailRepository;
import org.example.orderservice.models.repositories.OrderRepository;
import org.example.orderservice.models.services.OrderService;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final ProductGatewayService productGatewayService;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Override
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        Order order = new Order();
        order.setCustomerName(request.customerName());
        order.setStatus(OrderStatus.PENDING);
        order.setTotal(0.0);

        List<OrderDetail> orderDetails = new ArrayList<>();
        List<OrderDetailResponse> detailResponses = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (CreateOrderDetailRequest item : request.items()) {
            ProductResponse product = productGatewayService.getProductById(item.productId());
            if (product == null) {
                throw new ProductNotFoundException(item.productId());
            }

            BigDecimal unitPrice = BigDecimal.valueOf(product.price());
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(item.quantity()));
            totalAmount = totalAmount.add(subtotal);

            OrderDetail orderDetail = OrderDetail.builder()
                    .productId(product.id())
                    .quantity(item.quantity())
                    .unitPrice(product.price())
                    .build();

            orderDetails.add(orderDetail);
            detailResponses.add(new OrderDetailResponse(
                    null,
                    product.id(),
                    product.name(),
                    item.quantity(),
                    product.price(),
                    subtotal.doubleValue()
            ));
        }

        order.setTotal(totalAmount.doubleValue());
        Order savedOrder = orderRepository.save(order);

        for (int i = 0; i < orderDetails.size(); i++) {
            orderDetails.get(i).setOrder(savedOrder);
        }

        List<OrderDetail> savedOrderDetails = orderDetailRepository.saveAll(orderDetails);

        List<OrderDetailResponse> finalResponses = new ArrayList<>();
        for (int i = 0; i < savedOrderDetails.size(); i++) {
            OrderDetail savedDetail = savedOrderDetails.get(i);
            OrderDetailResponse original = detailResponses.get(i);
            finalResponses.add(new OrderDetailResponse(
                    savedDetail.getId(),
                    savedDetail.getProductId(),
                    original.productName(),
                    savedDetail.getQuantity(),
                    savedDetail.getUnitPrice(),
                    savedDetail.getUnitPrice() * savedDetail.getQuantity()
            ));
        }

        kafkaTemplate.send("order-created", request.customerEmail(), request.customerEmail());

        return new OrderResponse(
                savedOrder.getId(),
                savedOrder.getCustomerName(),
                savedOrder.getTotal(),
                savedOrder.getStatus(),
                finalResponses
        );
    }
}
