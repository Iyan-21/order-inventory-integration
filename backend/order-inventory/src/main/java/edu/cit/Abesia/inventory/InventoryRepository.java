package edu.cit.Abesia.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

// Package-private is fine here too, but Spring Data needs repositories to be
// at least accessible to the Spring context in the same package — keeping
// it public avoids any proxy-generation edge cases.
public interface InventoryRepository extends JpaRepository<Inventory, String> {
}