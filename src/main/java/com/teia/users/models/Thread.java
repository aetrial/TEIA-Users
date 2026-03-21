package com.teia.users.models;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.persistence.*;

@Entity
@Table(name = "threads")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Thread {

    @EmbeddedId
    private ThreadId id;

    public Thread(String toPhoneNumber, String fromPhoneNumber, String threadId) {
        this.id = new ThreadId(toPhoneNumber, fromPhoneNumber, threadId);
    }

    public String getToPhoneNumber() {
        return id != null ? id.getToPhoneNumber() : null;
    }

    public String getFromPhoneNumber() {
        return id != null ? id.getFromPhoneNumber() : null;
    }

    public String getThreadId() {
        return id != null ? id.getThreadId() : null;
    }
}
