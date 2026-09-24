package edu.cit.Abesia.supplier.internal;

import edu.cit.Abesia.supplier.SupplierOrder;
import edu.cit.Abesia.supplier.SupplierOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SupplierOrderRepository extends JpaRepository<SupplierOrder, Long> {
    Optional<SupplierOrder> findByBuyerRef(String buyerRef);
    List<SupplierOrder> findByStatus(SupplierOrderStatus status);
    List<SupplierOrder> findByStatusIn(List<SupplierOrderStatus> statuses);
}