import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { EmailService } from '../../services/email.service';
import { Email, EmailPart } from '../../models/email.model';

@Component({
  selector: 'app-email-detail',
  imports: [CommonModule],
  templateUrl: './email-detail.component.html',
  styleUrl: './email-detail.component.scss'
})
export class EmailDetailComponent implements OnInit {
  email: Email | null = null;
  displayParts: EmailPart[] = [];
  loading = true;
  error = '';

  constructor(
    private readonly emailService: EmailService,
    private readonly route: ActivatedRoute,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.emailService.getEmail(id).subscribe({
      next: (email) => {
        this.email = email;
        this.displayParts = this.sortPartsForDisplay(email.parts ?? []);
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

  hasRenderableParts(email: Email): boolean {
    return !!email.parts?.length;
  }

  isTextPart(part: EmailPart): boolean {
    const contentType = this.normalizeContentType(part.contentType);
    return contentType.startsWith('text/plain') || contentType.startsWith('text/html');
  }

  isHtmlPart(part: EmailPart): boolean {
    return this.normalizeContentType(part.contentType).startsWith('text/html');
  }

  getPartBadge(part: EmailPart): string {
    if (this.isHtmlPart(part)) {
      return 'HTML';
    }

    if (this.isTextPart(part)) {
      return 'TEXT';
    }

    return 'OTHER';
  }

  getPartBadgeClass(part: EmailPart): string {
    const badge = this.getPartBadge(part);

    if (badge === 'HTML') {
      return 'badge-html';
    }

    if (badge === 'TEXT') {
      return 'badge-text';
    }

    return 'badge-other';
  }

  shouldRenderFallbackHtml(email: Email): boolean {
    return !this.hasRenderableParts(email) && this.looksLikeHtml(email.body);
  }

  private normalizeContentType(contentType: string | null | undefined): string {
    return (contentType ?? '').toLowerCase();
  }

  private sortPartsForDisplay(parts: EmailPart[]): EmailPart[] {
    return [...parts].sort((a, b) => {
      const priorityA = this.getSortPriority(a);
      const priorityB = this.getSortPriority(b);

      if (priorityA !== priorityB) {
        return priorityA - priorityB;
      }

      return a.partIndex - b.partIndex;
    });
  }

  private getSortPriority(part: EmailPart): number {
    if (this.isHtmlPart(part)) {
      return 0;
    }

    if (this.isTextPart(part)) {
      return 1;
    }

    return 2;
  }

  private looksLikeHtml(content: string | null | undefined): boolean {
    if (!content) {
      return false;
    }

    return /<\/?[a-z][\s\S]*>/i.test(content);
  }
}
