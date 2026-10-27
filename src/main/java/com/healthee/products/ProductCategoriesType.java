package com.healthee.products;

public enum ProductCategoriesType {
    NORM("Norm"),
    FOODS("Foods"),
    EXOTIC("Exotic");

    private final String value;

    ProductCategoriesType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}