import { Routes } from '@angular/router';
import { EmailListComponent } from './components/email-list/email-list.component';
import { EmailDetailComponent } from './components/email-detail/email-detail.component';

export const routes: Routes = [
  { path: '', component: EmailListComponent },
  { path: 'emails/:id', component: EmailDetailComponent },
  { path: '**', redirectTo: '' }
];
