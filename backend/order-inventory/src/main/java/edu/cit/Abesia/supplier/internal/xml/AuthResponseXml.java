package edu.cit.Abesia.supplier.internal.xml;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

@JacksonXmlRootElement(localName = "AuthResponse")
public class AuthResponseXml {
    public String SessionToken;
    public String IssuedAt;
}