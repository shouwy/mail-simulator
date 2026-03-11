import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { Router } from '@angular/router';

import { EmailListComponent } from './email-list.component';
import { EmailService } from '../../services/email.service';
import { Email } from '../../models/email.model';

describe('EmailListComponent', () => {
  let component: EmailListComponent;
  let fixture: ComponentFixture<EmailListComponent>;
  let emailServiceSpy: jasmine.SpyObj<EmailService>;
  let routerSpy: jasmine.SpyObj<Router>;

  const emailsMock: Email[] = [
    {
      id: 1,
      from: 'sender@example.com',
      to: 'receiver@example.com',
      subject: 'Test',
      body: 'Hello',
      receivedAt: '2026-03-11T10:00:00Z'
    }
  ];

  beforeEach(async () => {
    emailServiceSpy = jasmine.createSpyObj<EmailService>('EmailService', ['getEmails']);
    routerSpy = jasmine.createSpyObj<Router>('Router', ['navigate']);
    emailServiceSpy.getEmails.and.returnValue(of([]));

    await TestBed.configureTestingModule({
      imports: [EmailListComponent],
      providers: [
        { provide: EmailService, useValue: emailServiceSpy },
        { provide: Router, useValue: routerSpy }
      ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(EmailListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should call loadEmails on init', () => {
    const loadSpy = spyOn(component, 'loadEmails');
    component.ngOnInit();

    expect(loadSpy).toHaveBeenCalled();
  });

  it('should load emails successfully', () => {
    emailServiceSpy.getEmails.and.returnValue(of(emailsMock));

    component.loadEmails();

    expect(component.emails).toEqual(emailsMock);
    expect(component.error).toBe('');
    expect(component.loading).toBeFalse();
  });

  it('should set an error message when loading emails fails', () => {
    emailServiceSpy.getEmails.and.returnValue(throwError(() => new Error('boom')));

    component.loadEmails();

    expect(component.emails).toEqual([]);
    expect(component.error).toBe('Failed to load emails. Make sure the backend is running.');
    expect(component.loading).toBeFalse();
  });

  it('should navigate to the email detail page', () => {
    component.viewEmail(42);

    expect(routerSpy.navigate).toHaveBeenCalledWith(['/emails', 42]);
  });

  it('should prevent default and navigate on space key handler', () => {
    const event = jasmine.createSpyObj<Event>('event', ['preventDefault']);
    const viewSpy = spyOn(component, 'viewEmail');

    component.onRowSpaceKey(event, 7);

    expect(event.preventDefault).toHaveBeenCalled();
    expect(viewSpy).toHaveBeenCalledWith(7);
  });
});
