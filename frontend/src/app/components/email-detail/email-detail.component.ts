import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { EmailService } from '../../services/email.service';
import { Email } from '../../models/email.model';

@Component({
  selector: 'app-email-detail',
  imports: [CommonModule],
  templateUrl: './email-detail.component.html',
  styleUrl: './email-detail.component.scss'
})
export class EmailDetailComponent implements OnInit {
  email: Email | null = null;
  loading = true;
  error = '';

  constructor(
    private emailService: EmailService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.emailService.getEmail(id).subscribe({
      next: (email) => {
        this.email = email;
        this.loading = false;
      },
      error: () => {
        this.error = 'Email not found.';
        this.loading = false;
      }
    });
  }

  goBack(): void {
    this.router.navigate(['/']);
  }
}
