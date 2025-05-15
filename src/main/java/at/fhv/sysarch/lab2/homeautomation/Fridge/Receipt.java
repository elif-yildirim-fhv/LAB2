package at.fhv.sysarch.lab2.homeautomation.Fridge;

import java.time.LocalDateTime;
import java.util.List;

public record Receipt(String orderId, List<Product> items, int[] quantities, double totalPrice, LocalDateTime timestamp) {}
