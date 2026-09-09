import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { Employee, LeaveRequest } from '../models/leave-request.model';

function calendarDay(value: string): number | null {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value) || value.startsWith('0000')) return null;
  const timestamp = Date.parse(value + 'T00:00:00Z');
  return Number.isFinite(timestamp) && new Date(timestamp).toISOString().slice(0, 10) === value
    ? timestamp / 86400000 : null;
}

// NOTE: This component was written quickly for a POC.
// It talks to the API directly, manages state by hand and uses `any` everywhere.
@Component({
  selector: 'app-leave-requests',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './leave-requests.component.html',
  styleUrls: ['./leave-requests.component.css']
})
export class LeaveRequestsComponent implements OnInit {
  requests: any[] = [];
  loading = false;
  employees: Employee[] = [];
  employeesLoading = false;
  employeesError = '';
  submitting = false;
  submitError = '';
  submitSuccess = '';
  private readonly destroyRef = inject(DestroyRef);
  readonly createForm = new FormGroup({
    employeeId: new FormControl<number | null>(null, [Validators.required,
      control => control.value === null || this.employees.some(e => e.id === control.value)
        ? null : { employee: true }]),
    type: new FormControl<number | null>(null, [Validators.required,
      control => control.value === null || [0, 1, 2].includes(control.value)
        ? null : { leaveType: true }]),
    startDate: new FormControl('', { nonNullable: true, validators: [Validators.required,
      control => !control.value || calendarDay(control.value) !== null ? null : { date: true }] }),
    endDate: new FormControl('', { nonNullable: true, validators: [Validators.required,
      control => !control.value || calendarDay(control.value) !== null ? null : { date: true }] })
  }, { validators: control => {
    const start = calendarDay(control.get('startDate')?.value ?? '');
    const end = calendarDay(control.get('endDate')?.value ?? '');
    return start !== null && end !== null && start > end ? { dateOrder: true } : null;
  }});

  get requestedDays(): number | null {
    const start = calendarDay(this.createForm.controls.startDate.value);
    const end = calendarDay(this.createForm.controls.endDate.value);
    return start !== null && end !== null && end >= start ? end - start + 1 : null;
  }

  loadEmployees(): void {
    if (this.employeesLoading) return;
    this.employeesLoading = true;
    this.employeesError = '';
    this.http.get<Employee[]>('http://localhost:5080/api/employees').pipe(
      takeUntilDestroyed(this.destroyRef), finalize(() => this.employeesLoading = false)
    ).subscribe({
      next: employees => {
        this.employees = employees;
        this.createForm.controls.employeeId.updateValueAndValidity();
      },
      error: () => this.employeesError = 'Could not load employees. Please try again.'
    });
  }

  submit(): void {
    if (this.submitting || this.employeesLoading || this.loading) return;
    this.submitSuccess = '';
    this.submitError = '';
    this.createForm.markAllAsTouched();
    if (this.createForm.invalid || this.employeesError || !this.employees.length) return;
    const values = this.createForm.getRawValue();
    const body = { employeeId: values.employeeId!, type: values.type!,
      startDate: values.startDate, endDate: values.endDate };
    this.submitting = true;
    this.http.post<LeaveRequest>(this.apiUrl, body).pipe(
      takeUntilDestroyed(this.destroyRef), finalize(() => this.submitting = false)
    ).subscribe({
      next: request => {
        const created = { ...request, employee: request.employee ??
          this.employees.find(employee => employee.id === body.employeeId) };
        this.requests = [created, ...this.requests.filter(r => r.id !== created.id)]
          .sort((a, b) => b.startDate.localeCompare(a.startDate));
        this.createForm.reset();
        this.submitSuccess = 'Leave request submitted successfully.';
      },
      error: (error: HttpErrorResponse) => {
        this.submitError = error.status === 0 ? 'Cannot reach the server. Please try again.'
          : error.status === 400 && error.error === 'Not enough vacation balance'
          ? 'Not enough vacation balance. Shorten the request or choose different dates.'
          : error.status === 400 ? 'The request was rejected. Check the fields and try again.'
          : error.status === 404 ? 'The employee was not found. Reload employees and select again.'
          : 'Could not submit the request. Please try again.';
      }
    });
  }

  private apiUrl = 'http://localhost:5080/api/leave-requests';

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.load();
    this.loadEmployees();
  }

  load(): void {
    this.loading = true;
    this.http.get<any>(this.apiUrl).subscribe({ next: (data) => {
      this.requests = data;
      this.loading = false;
    }, error: () => { this.loading = false; } });
  }

  // Wired up by the candidate as part of the assignment.
  approve(id: number): void {
    // TODO (candidate): call POST /api/leave-requests/{id}/approve
    // and handle loading / error / success without a generic alert.
    this.http.post<any>(this.apiUrl + '/' + id + '/approve', {}).subscribe(() => {
      this.load();
    });
  }

  typeLabel(type: number): string {
    if (type == 0) return 'Vacation';
    if (type == 1) return 'Sick';
    return 'Unpaid';
  }

  statusLabel(status: number): string {
    if (status == 0) return 'Pending';
    if (status == 1) return 'Approved';
    return 'Rejected';
  }
}
