package at.fhv.sysarch.lab2.homeautomation.order;

import at.fhv.sysarch.lab2.homeautomation.Fridge.Product;
import at.fhv.sysarch.lab2.homeautomation.Fridge.Receipt;

public record Order(Product product, int amount, Receipt receipt) {}
