package com.mailsimulator.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;

@Entity
@Table(name = "email_attachments")
public class EmailAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "charset")
    private String charset;

    @Column(name = "transfer_encoding")
    private String transferEncoding;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "email_id")
    @JsonBackReference
    private Email email;

    public EmailAttachment() {
    }

    public EmailAttachment(String fileName, String contentType, String charset, String transferEncoding, Long sizeBytes) {
        this.fileName = fileName;
        this.contentType = contentType;
        this.charset = charset;
        this.transferEncoding = transferEncoding;
        this.sizeBytes = sizeBytes;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getCharset() {
        return charset;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }

    public String getTransferEncoding() {
        return transferEncoding;
    }

    public void setTransferEncoding(String transferEncoding) {
        this.transferEncoding = transferEncoding;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public Email getEmail() {
        return email;
    }

    public void setEmail(Email email) {
        this.email = email;
    }
}
