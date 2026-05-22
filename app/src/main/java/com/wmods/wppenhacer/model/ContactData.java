package com.wmods.wppenhacer.model;

import java.io.Serializable;

public class ContactData implements Serializable {
    private final String name;
    private final String jid;

    public ContactData(String name, String jid) {
        this.name = name;
        this.jid = jid;
    }

    public String getName() {
        return name;
    }

    public String getJid() {
        return jid;
    }

    public String getDisplayName() {
        if (name != null && !name.isEmpty()) {
            return name;
        }
        return jid != null ? jid.split("@")[0] : "";
    }
}