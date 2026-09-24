package edu.cit.Abesia.supplier.internal.xml;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

@JacksonXmlRootElement(localName = "AuthRequest")
public class AuthRequestXml {
    public String ClientId;
    public String ApiKey;

    public AuthRequestXml() {}
    public AuthRequestXml(String clientId, String apiKey) {
        this.ClientId = clientId;
        this.ApiKey = apiKey;
    }
}