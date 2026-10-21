import { ChangeDetectionStrategy, Component, inject, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatInputModule } from '@angular/material/input';

import { Api } from '../core/api';

/** An agent files a ticket on the customer's behalf (phone call, email). */
@Component({
  selector: 'app-new-ticket',
  imports: [FormsModule, MatInputModule, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form class="box" (ngSubmit)="submit()">
      <mat-form-field class="email" subscriptSizing="dynamic">
        <mat-label>Customer email</mat-label>
        <input matInput type="email" name="email" [(ngModel)]="email" />
      </mat-form-field>
      <mat-form-field class="text" subscriptSizing="dynamic">
        <mat-label>Ticket text</mat-label>
        <textarea matInput name="text" rows="3" maxlength="2000" [(ngModel)]="text" required></textarea>
      </mat-form-field>
      <button mat-flat-button type="submit" [disabled]="!text().trim() || busy()">Create and triage</button>
    </form>
  `,
  styles: `
    .box { display: flex; gap: 12px; align-items: flex-start; flex-wrap: wrap; padding: 12px; margin-bottom: 12px;
           border: 1px solid var(--mat-sys-outline-variant); border-radius: 8px; }
    .email { width: 240px; }
    .text { flex: 1; min-width: 280px; }
  `,
})
export class NewTicketForm {
  private readonly api = inject(Api);
  readonly created = output<number>();
  protected readonly email = signal('');
  protected readonly text = signal('');
  protected readonly busy = signal(false);

  protected submit(): void {
    this.busy.set(true);
    this.api.createTicket(this.text().trim(), this.email().trim()).subscribe({
      next: (r) => {
        this.busy.set(false);
        this.text.set('');
        this.created.emit(r.id);
      },
      error: () => this.busy.set(false),
    });
  }
}
