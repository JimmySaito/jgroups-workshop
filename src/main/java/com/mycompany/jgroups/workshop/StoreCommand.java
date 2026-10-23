package com.mycompany.jgroups.workshop;

import java.io.Serializable;
import java.util.UUID;

public class StoreCommand implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final String operationId;
    private final String key;
    private final String value;

    public StoreCommand(String key, String value) {
        this.operationId = UUID.randomUUID().toString();
        this.key = key;
        this.value = value;
    }

    public String getOperationId() {
        return operationId;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }
}