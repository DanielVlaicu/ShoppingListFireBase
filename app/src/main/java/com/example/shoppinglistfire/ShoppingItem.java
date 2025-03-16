package com.example.shoppinglistfire;

public class ShoppingItem {
    private String id;
    private String name;

    // Constructor gol necesar pentru Firebase
    public ShoppingItem() {}

    // Constructor cu parametri
    public ShoppingItem(String id, String name) {
        this.id = id;
        this.name = name;
    }

    // Getteri și setteri
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}