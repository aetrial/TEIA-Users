package com.teia.users.models;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ThreadId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "to_phone_number", length = 255, nullable = false)
    private String toPhoneNumber;

    @Column(name = "from_phone_number", length = 255, nullable = false)
    private String fromPhoneNumber;

    @Column(name = "thread_id", length = 255, nullable = false)
    private String threadId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ThreadId that = (ThreadId) o;
        return Objects.equals(toPhoneNumber, that.toPhoneNumber) &&
               Objects.equals(fromPhoneNumber, that.fromPhoneNumber) &&
               Objects.equals(threadId, that.threadId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(toPhoneNumber, fromPhoneNumber, threadId);
    }
}
