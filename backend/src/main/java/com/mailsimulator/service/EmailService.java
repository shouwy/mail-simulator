package com.mailsimulator.service;

import com.mailsimulator.entity.Email;
import com.mailsimulator.repository.EmailRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class EmailService {

    private final EmailRepository emailRepository;

    public EmailService(EmailRepository emailRepository) {
        this.emailRepository = emailRepository;
    }

    public Email save(Email email) {
        if (email == null) {
            throw new IllegalArgumentException("Email cannot be null");
        }
        return emailRepository.save(email);
    }

    public List<Email> findAll() {
        return emailRepository.findAll();
    }

    public Optional<Email> findById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Id cannot be null");
        }
        return emailRepository.findById(id);
    }
}
