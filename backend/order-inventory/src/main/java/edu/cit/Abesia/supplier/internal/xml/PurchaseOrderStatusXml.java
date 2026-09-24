package edu.cit.Abesia.supplier.internal.xml;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

@JacksonXmlRootElement(localName = "PurchaseOrderStatus")
public class PurchaseOrderStatusXml {
    public String PoNumber;
    public int StatusCode;
    public String SupplierSku;
    public int Qty;
    public String Uom;
    public String BuyerRef;
    public String CreatedAt;
    public String CheckedAt;
}