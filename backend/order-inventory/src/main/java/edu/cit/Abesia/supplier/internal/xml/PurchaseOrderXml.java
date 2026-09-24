package edu.cit.Abesia.supplier.internal.xml;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

@JacksonXmlRootElement(localName = "PurchaseOrder")
public class PurchaseOrderXml {
    public String SupplierSku;
    public int Qty;
    public String BuyerRef;

    public PurchaseOrderXml() {}
    public PurchaseOrderXml(String supplierSku, int qty, String buyerRef) {
        this.SupplierSku = supplierSku;
        this.Qty = qty;
        this.BuyerRef = buyerRef;
    }
}