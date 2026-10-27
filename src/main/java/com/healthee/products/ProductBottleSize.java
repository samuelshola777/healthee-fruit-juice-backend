package com.healthee.products;

public enum ProductBottleSize {
    CL_25("25cl"),
    CL_50("50cl");

    private final String value;

    ProductBottleSize(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}