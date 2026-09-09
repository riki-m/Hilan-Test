import { Component } from '@angular/core';
import { LeaveRequestsComponent } from './leave-requests/leave-requests.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [LeaveRequestsComponent],
  template: `
    <header class="app-header"><span class="brand-mark" aria-hidden="true">L</span><h1>Leave Management</h1><span>Employee workspace</span></header>
    <main><app-leave-requests></app-leave-requests></main>
  `
})
export class AppComponent {}
