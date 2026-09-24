package edu.cit.Abesia.supplier.internal.xml;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

@JacksonXmlRootElement(localName = "LSError")
public class LSErrorXml {
    public String Code;
    public String Message;
}