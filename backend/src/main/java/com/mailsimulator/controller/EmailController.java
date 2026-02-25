package com.mailsimulator.controller;

import com.mailsimulator.entity.Email;
import com.mailsimulator.service.EmailService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/emails")
public class EmailController {

    private final EmailService emailService;

    public EmailController(EmailService emailService) {
        this.emailService = emailService;
    }

    @GetMapping
    public List<Email> listEmails() {
        return emailService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Email> getEmail(@PathVariable Long id) {
        return emailService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
