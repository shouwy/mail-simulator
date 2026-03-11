package com.mailsimulator.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;

@Entity
@Table(name = "email_parts")
public class EmailPart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "part_index")
    private Integer partIndex;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "charset")
    private String charset;

    @Column(name = "transfer_encoding")
    private String transferEncoding;

    @Lob
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "email_id")
    @JsonBackReference
    private Email email;

    public EmailPart() {
    }

    public EmailPart(Integer partIndex, String contentType, String charset, String transferEncoding, String content) {
        this.partIndex = partIndex;
        this.contentType = contentType;
        this.charset = charset;
        this.transferEncoding = transferEncoding;
        this.content = content;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getPartIndex() {
        return partIndex;
    }

    public void setPartIndex(Integer partIndex) {
        this.partIndex = partIndex;
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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Email getEmail() {
        return email;
    }

    public void setEmail(Email email) {
        this.email = email;
    }
}
