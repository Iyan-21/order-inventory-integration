package edu.cit.Abesia.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    @Column(name = "product_id")
    private String productId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int stock;

    protected Inventory() {
        // JPA requires a no-arg constructor
    }

    public String getProductId() {
        return productId;
    }

    public String getName() {
        return name;
    }

    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }
}