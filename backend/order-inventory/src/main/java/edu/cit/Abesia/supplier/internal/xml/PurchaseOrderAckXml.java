package edu.cit.Abesia.supplier.internal.xml;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

@JacksonXmlRootElement(localName = "PurchaseOrderAck")
public class PurchaseOrderAckXml {
    public String PoNumber;
    public int StatusCode;
    public String SupplierSku;
    public int Qty;
    public String Uom;
    public String BuyerRef;
    public String CreatedAt;
}