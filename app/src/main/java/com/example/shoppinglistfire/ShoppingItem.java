package com.example.shoppinglistfire;

import androidx.annotation.NonNull;

public class ShoppingItem {
    private String id;
    private String name;
    private String description;

    // Constructor gol necesar pentru Firebase
    public ShoppingItem() {
    }

    // Constructor cu parametri
    public ShoppingItem(String id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }


}