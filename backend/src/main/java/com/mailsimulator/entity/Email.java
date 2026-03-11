package com.mailsimulator.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "emails")
public class Email {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sender")
    private String from;

    @Column(name = "recipient")
    private String to;

    @Column(name = "subject")
    private String subject;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @OneToMany(mappedBy = "email", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JsonManagedReference
    private List<EmailPart> parts = new ArrayList<>();

    @OneToMany(mappedBy = "email", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JsonManagedReference
    private List<EmailAttachment> attachments = new ArrayList<>();

    public Email() {}

    public Email(String from, String to, String subject, String body, LocalDateTime receivedAt) {
        this.from = from;
        this.to = to;
        this.subject = subject;
        this.body = body;
        this.receivedAt = receivedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }

    public String getTo() { return to; }
    public void setTo(String to) { this.to = to; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public LocalDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(LocalDateTime receivedAt) { this.receivedAt = receivedAt; }

    public List<EmailPart> getParts() { return parts; }
    public void setParts(List<EmailPart> parts) {
        this.parts.clear();
        if (parts != null) {
            for (EmailPart part : parts) {
                addPart(part);
            }
        }
    }

    public List<EmailAttachment> getAttachments() { return attachments; }
    public void setAttachments(List<EmailAttachment> attachments) {
        this.attachments.clear();
        if (attachments != null) {
            for (EmailAttachment attachment : attachments) {
                addAttachment(attachment);
            }
        }
    }

    public void addPart(EmailPart part) {
        if (part == null) {
            return;
        }
        part.setEmail(this);
        this.parts.add(part);
    }

    public void addAttachment(EmailAttachment attachment) {
        if (attachment == null) {
            return;
        }
        attachment.setEmail(this);
        this.attachments.add(attachment);
    }
}
