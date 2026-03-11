import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { EmailDetailComponent } from './email-detail.component';
import { EmailService } from '../../services/email.service';
import { Email, EmailPart } from '../../models/email.model';

describe('EmailDetailComponent', () => {
  let component: EmailDetailComponent;
  let fixture: ComponentFixture<EmailDetailComponent>;
  let emailServiceSpy: jasmine.SpyObj<EmailService>;
  let routerSpy: jasmine.SpyObj<Router>;
  let routeId: string | null;

  const emailMock: Email = {
    id: 10,
    from: 'sender@example.com',
    to: 'receiver@example.com',
    subject: 'Subject',
    body: '<p>Hello</p>',
    receivedAt: '2026-03-11T10:00:00Z',
    parts: [
      { id: 1, partIndex: 2, contentType: 'application/json', charset: null, transferEncoding: null, content: '{}' },
      { id: 2, partIndex: 3, contentType: 'text/plain', charset: 'UTF-8', transferEncoding: '8bit', content: 'plain' },
      { id: 3, partIndex: 1, contentType: 'text/html', charset: 'UTF-8', transferEncoding: 'quoted-printable', content: '<b>html</b>' }
    ]
  };

  beforeEach(async () => {
    routeId = '10';
    emailServiceSpy = jasmine.createSpyObj<EmailService>('EmailService', ['getEmail']);
    routerSpy = jasmine.createSpyObj<Router>('Router', ['navigate']);
    emailServiceSpy.getEmail.and.returnValue(of(emailMock));

    await TestBed.configureTestingModule({
      imports: [EmailDetailComponent],
      providers: [
        { provide: EmailService, useValue: emailServiceSpy },
        { provide: Router, useValue: routerSpy },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: {
                get: () => routeId
              }
            }
          }
        }
      ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(EmailDetailComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load email details on init and sort parts for display', () => {
    component.ngOnInit();

    expect(emailServiceSpy.getEmail).toHaveBeenCalledWith(10);
    expect(component.email).toEqual(emailMock);
    expect(component.loading).toBeFalse();
    expect(component.displayParts.map((p) => p.contentType)).toEqual([
      'text/html',
      'text/plain',
      'application/json'
    ]);
  });

  it('should set invalid id error and skip service call when route id is invalid', () => {
    routeId = 'not-a-number';

    component.ngOnInit();

    expect(emailServiceSpy.getEmail).not.toHaveBeenCalled();
    expect(component.error).toBe('Invalid email id.');
    expect(component.loading).toBeFalse();
  });

  it('should set not found error when API call fails', () => {
    emailServiceSpy.getEmail.and.returnValue(throwError(() => new Error('404')));

    component.ngOnInit();

    expect(component.error).toBe('Email not found.');
    expect(component.loading).toBeFalse();
  });

  it('should navigate back to email list', () => {
    component.goBack();

    expect(routerSpy.navigate).toHaveBeenCalledWith(['/']);
  });

  it('should classify text and html parts correctly', () => {
    const textPart: EmailPart = {
      id: 1,
      partIndex: 0,
      contentType: 'text/plain; charset=utf-8',
      charset: 'utf-8',
      transferEncoding: null,
      content: 'plain'
    };
    const htmlPart: EmailPart = {
      ...textPart,
      id: 2,
      contentType: 'text/html; charset=utf-8',
      content: '<p>html</p>'
    };
    const otherPart: EmailPart = {
      ...textPart,
      id: 3,
      contentType: 'application/json',
      content: '{}'
    };

    expect(component.isTextPart(textPart)).toBeTrue();
    expect(component.isHtmlPart(htmlPart)).toBeTrue();
    expect(component.getPartBadge(htmlPart)).toBe('HTML');
    expect(component.getPartBadge(textPart)).toBe('TEXT');
    expect(component.getPartBadge(otherPart)).toBe('OTHER');
    expect(component.getPartBadgeClass(htmlPart)).toBe('badge-html');
    expect(component.getPartBadgeClass(textPart)).toBe('badge-text');
    expect(component.getPartBadgeClass(otherPart)).toBe('badge-other');
  });

  it('should decide fallback html rendering based on body and parts', () => {
    const withRenderableParts: Email = {
      ...emailMock,
      body: '<div>html</div>',
      parts: [
        {
          id: 1,
          partIndex: 0,
          contentType: 'text/plain',
          charset: null,
          transferEncoding: null,
          content: 'text'
        }
      ]
    };
    const noPartsHtmlBody: Email = {
      ...emailMock,
      parts: [],
      body: '<div>html</div>'
    };
    const noPartsPlainBody: Email = {
      ...emailMock,
      parts: [],
      body: 'plain text'
    };

    expect(component.hasRenderableParts(withRenderableParts)).toBeTrue();
    expect(component.shouldRenderFallbackHtml(withRenderableParts)).toBeFalse();
    expect(component.shouldRenderFallbackHtml(noPartsHtmlBody)).toBeTrue();
    expect(component.shouldRenderFallbackHtml(noPartsPlainBody)).toBeFalse();
  });
});
