import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';

import { EmailService } from './email.service';
import { Email } from '../models/email.model';

describe('EmailService', () => {
  let service: EmailService;
  let httpMock: HttpTestingController;

  const emailsMock: Email[] = [
    {
      id: 1,
      from: 'sender@example.com',
      to: 'receiver@example.com',
      subject: 'Hello',
      body: 'Body',
      receivedAt: '2026-03-11T10:00:00Z'
    }
  ];

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });
    service = TestBed.inject(EmailService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should fetch email list from API', () => {
    service.getEmails().subscribe((emails) => {
      expect(emails).toEqual(emailsMock);
    });

    const req = httpMock.expectOne('http://localhost:8090/api/emails');
    expect(req.request.method).toBe('GET');
    req.flush(emailsMock);
  });

  it('should fetch one email by id from API', () => {
    const email = emailsMock[0];

    service.getEmail(1).subscribe((result) => {
      expect(result).toEqual(email);
    });

    const req = httpMock.expectOne('http://localhost:8090/api/emails/1');
    expect(req.request.method).toBe('GET');
    req.flush(email);
  });
});
