import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { LeaveRequestsComponent } from './leave-requests.component';

describe('F1 leave request form', () => {
  let fixture: ComponentFixture<LeaveRequestsComponent>;
  let component: LeaveRequestsComponent;
  let http: HttpTestingController;
  const api = 'http://localhost:5080/api/leave-requests';
  const employeesUrl = 'http://localhost:5080/api/employees';
  const employees = [{ id: 1, name: 'Test employee', annualQuota: 20 }];
  const values = { employeeId: 1, type: 0, startDate: '2026-03-01', endDate: '2026-03-03' };
  const created = { ...values, id: 10, days: 3, status: 0 };

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [LeaveRequestsComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()] }).compileComponents();
    fixture = TestBed.createComponent(LeaveRequestsComponent);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne(api).flush([]);
    http.expectOne(employeesUrl).flush(employees);
    fixture.detectChanges();
  });
  afterEach(() => http.verify());

  it('shows field errors on empty submit and sends no POST', () => {
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Select an available employee.');
    expect(fixture.nativeElement.textContent).toContain('Select a leave type.');
    expect(fixture.nativeElement.textContent).toContain('Enter a valid start date.');
    expect(fixture.nativeElement.textContent).toContain('Enter a valid end date.');
    http.expectNone(api);
  });

  it('rejects each missing field, invalid selections and invalid dates', () => {
    for (const patch of [{ employeeId: null }, { type: null }, { startDate: '' }, { endDate: '' },
      { employeeId: 99 }, { type: 9 }, { startDate: '2026-02-30' }]) {
      component.createForm.setValue(values);
      component.createForm.patchValue(patch);
      component.submit();
      expect(component.createForm.invalid).toBeTrue();
    }
    http.expectNone(api);
  });

  it('rejects reversed dates without displaying negative days', () => {
    component.createForm.setValue({ ...values, endDate: '2026-02-28' });
    component.submit();
    fixture.detectChanges();
    expect(component.requestedDays).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('End date must be on or after');
    http.expectNone(api);
  });

  it('counts a single day, leap day and year boundary inclusively', () => {
    for (const [startDate, endDate, expected] of [
      ['2026-03-01', '2026-03-01', 1], ['2024-02-28', '2024-03-01', 3],
      ['2026-12-31', '2027-01-01', 2]] as const) {
      component.createForm.setValue({ ...values, startDate, endDate });
      expect(component.requestedDays).toBe(expected);
    }
  });

  it('sends numeric type and dates, prevents duplicate submit and appends without reloading', () => {
    component.requests = [{ ...created, id: 9 }];
    component.createForm.setValue(values);
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    component.submit();
    const request = http.expectOne(api);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(values);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('button[type=submit]').disabled).toBeTrue();
    request.flush(created);
    fixture.detectChanges();
    expect(component.requests.map(r => r.id)).toEqual([10, 9]);
    expect(component.requests[0].employee.name).toBe('Test employee');
    expect(component.createForm.pristine).toBeTrue();
    expect(component.submitSuccess).toContain('successfully');
    expect(component.submitting).toBeFalse();
    http.expectNone(api);
  });

  for (const [status, body, message] of [
    [400, 'Not enough vacation balance', 'Not enough vacation balance'],
    [400, 'Validation failed', 'Check the fields'],
    [404, 'Employee not found', 'employee was not found'],
    [500, 'internal details', 'Could not submit']
  ] as const) {
    it(`handles ${status} ${body} and allows a retry without losing input`, () => {
      component.createForm.setValue(values);
      component.submit();
      http.expectOne(api).flush(body, { status, statusText: 'Error' });
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('[role=alert]').textContent).toContain(message);
      expect(component.createForm.getRawValue()).toEqual(values);
      expect(component.submitting).toBeFalse();
      component.submit();
      http.expectOne(api).flush(created);
      expect(component.submitError).toBe('');
    });
  }

  it('handles a network failure and retries', () => {
    component.createForm.setValue(values);
    component.submit();
    http.expectOne(api).error(new ProgressEvent('error'));
    expect(component.submitError).toContain('Cannot reach the server');
    expect(component.submitting).toBeFalse();
    component.submit();
    http.expectOne(api).flush(created);
  });

  it('blocks creation after employee lookup fails and supports reload', () => {
    component.loadEmployees();
    http.expectOne(employeesUrl).error(new ProgressEvent('error'));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Could not load employees');
    expect(fixture.nativeElement.querySelector('button[type=submit]').disabled).toBeTrue();
    component.createForm.setValue(values);
    component.submit();
    http.expectNone(api);
    component.loadEmployees();
    http.expectOne(employeesUrl).flush(employees);
    component.submit();
    http.expectOne(api).flush(created);
  });

  it('blocks creation when no employees exist', () => {
    component.loadEmployees();
    http.expectOne(employeesUrl).flush([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No employees available.');
    expect(fixture.nativeElement.querySelector('button[type=submit]').disabled).toBeTrue();
    component.createForm.setValue(values);
    component.submit();
    http.expectNone(api);
  });
});
