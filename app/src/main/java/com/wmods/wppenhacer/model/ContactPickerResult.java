package com.wmods.wppenhacer.model;


import java.io.Serializable;

public record ContactPickerResult(String jid, String fullName) implements Serializable {

    public ContactData toContactData() {
        return new ContactData(fullName, jid);
    }
}