import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';
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

  constructor(private readonly emailService: EmailService, private readonly router: Router) {}

  ngOnInit(): void {
    this.loadEmails();
  }

  loadEmails(): void {
    this.loading = true;
    this.error = '';
    this.emailService.getEmails()
      .pipe(finalize(() => {
        this.loading = false;
      }))
      .subscribe({
        next: (emails) => {
          this.emails = emails;
        },
        error: () => {
          this.error = 'Failed to load emails. Make sure the backend is running.';
        }
      });
  }

  viewEmail(id: number): void {
    this.router.navigate(['/emails', id]);
  }

  onRowSpaceKey(event: Event, id: number): void {
    event.preventDefault();
    this.viewEmail(id);
  }
}
