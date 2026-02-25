import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { EmailService } from '../../services/email.service';
import { Email } from '../../models/email.model';

@Component({
  selector: 'app-email-list',
  imports: [CommonModule],
  templateUrl: './email-list.component.html',
  styleUrl: './email-list.component.scss'
})
export class EmailListComponent implements OnInit {
  emails: Email[] = [];
  loading = true;
  error = '';

  constructor(private emailService: EmailService, private router: Router) {}

  ngOnInit(): void {
    this.loadEmails();
  }

  loadEmails(): void {
    this.loading = true;
    this.error = '';
    this.emailService.getEmails().subscribe({
      next: (emails) => {
        this.emails = emails;
        this.loading = false;
      },
      error: () => {
        this.error = 'Failed to load emails. Make sure the backend is running.';
        this.loading = false;
      }
    });
  }

  viewEmail(id: number): void {
    this.router.navigate(['/emails', id]);
  }
}
