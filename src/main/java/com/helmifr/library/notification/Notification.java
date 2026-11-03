package com.helmifr.library.notification;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/** An in-app message for one account. */
@Entity
@Getter
@Setter
@Table(name = "notifications")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40)
    private NotificationType type;

    @Column(name = "message", nullable = false, length = 500)
    private String message;

    /** In-app route to open, e.g. /my/loans. */
    @Column(name = "link")
    private String link;

    /** The English template of the message ({0}-style values in messageArgs); null for older rows. */
    @Column(name = "message_key", length = 500)
    private String messageKey;

    /** The template's values as a JSON array of strings. */
    @Column(name = "message_args", length = 1000)
    private String messageArgs;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
