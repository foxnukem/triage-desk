import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Router } from '@angular/router';

import { Api } from '../core/api';
import { CATEGORY_LABEL, age } from '../core/format';
import { CATEGORIES, PRIORITIES, STATUSES, Stats, TicketRow } from '../core/models';
import { NewTicketForm } from './new-ticket';

const REFRESH_MS = 5000;

@Component({
  selector: 'app-queue',
  imports: [
    FormsModule, MatTableModule, MatSelectModule, MatButtonToggleModule, MatSlideToggleModule, MatPaginatorModule,
    MatProgressBarModule, MatIconModule, MatButtonModule, MatTooltipModule, NewTicketForm,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <div class="row head">
        <h1>Queue</h1>
        <span class="grow"></span>
        @if (stats(); as s) {
          <span class="muted stat">new {{ s.byStatus.NEW }} · in review {{ s.byStatus.IN_REVIEW }} · triaged {{ s.byStatus.TRIAGED }}
            · resolved {{ s.byStatus.RESOLVED }} · escalated {{ s.byStatus.ESCALATED }}</span>
          @if (s.acceptedWithoutEdits != null) {
            <span class="chip good" matTooltip="Share of reviewed drafts accepted without edits">
              accepted as-is {{ (s.acceptedWithoutEdits * 100).toFixed(0) }}%</span>
          }
        }
        <button mat-stroked-button (click)="showNew.set(!showNew())"><mat-icon>add</mat-icon> New ticket</button>
      </div>

      @if (armRows().length > 1) {
        <div class="row arms">
          <span class="muted">A/B by classifier:</span>
          @for (a of armRows(); track a.name) {
            <span class="chip">{{ a.name === 'laya' ? 'Laya' : 'LLM' }}: {{ a.s.tickets }} tickets ·
              {{ a.s.needsHuman }} to human · accepted as-is
              {{ a.s.acceptedWithoutEdits == null ? '–' : (a.s.acceptedWithoutEdits * 100).toFixed(0) + '%' }}
              ({{ a.s.ACCEPTED + a.s.EDITED + a.s.REJECTED }} reviewed)</span>
          }
        </div>
      }

      @if (showNew()) {
        <app-new-ticket (created)="onCreated($event)" />
      }

      <div class="row filters">
        <mat-form-field subscriptSizing="dynamic">
          <mat-label>Status</mat-label>
          <mat-select [(ngModel)]="status" (selectionChange)="reload()">
            <mat-option value="">Any</mat-option>
            @for (s of statuses; track s) { <mat-option [value]="s">{{ s }}</mat-option> }
          </mat-select>
        </mat-form-field>
        <mat-form-field subscriptSizing="dynamic">
          <mat-label>Category</mat-label>
          <mat-select [(ngModel)]="category" (selectionChange)="reload()">
            <mat-option value="">Any</mat-option>
            @for (c of categories; track c) { <mat-option [value]="c">{{ categoryLabel[c] }}</mat-option> }
          </mat-select>
        </mat-form-field>
        <mat-form-field subscriptSizing="dynamic" class="narrow">
          <mat-label>Priority</mat-label>
          <mat-select [(ngModel)]="priority" (selectionChange)="reload()">
            <mat-option value="">Any</mat-option>
            @for (p of priorities; track p) { <mat-option [value]="p">{{ p }}</mat-option> }
          </mat-select>
        </mat-form-field>
        <mat-slide-toggle [(ngModel)]="humanOnly" (change)="reload()">Needs human</mat-slide-toggle>
        <span class="grow"></span>
        <mat-button-toggle-group [(ngModel)]="sort" (change)="reload()" aria-label="Sort">
          <mat-button-toggle value="priority">Priority</mat-button-toggle>
          <mat-button-toggle value="newest">Newest</mat-button-toggle>
          <mat-button-toggle value="oldest">Oldest</mat-button-toggle>
        </mat-button-toggle-group>
      </div>

      <div class="table-wrap">
        @if (loading()) { <mat-progress-bar mode="indeterminate" /> }
        <table mat-table [dataSource]="rows()">
          <ng-container matColumnDef="id">
            <th mat-header-cell *matHeaderCellDef>#</th>
            <td mat-cell *matCellDef="let r" class="mono">{{ r.id }}</td>
          </ng-container>
          <ng-container matColumnDef="priority">
            <th mat-header-cell *matHeaderCellDef>Priority</th>
            <td mat-cell *matCellDef="let r">
              @if (r.priority) {
                <span class="chip" [class]="r.priority.toLowerCase()">{{ r.priority }}</span>
                @if (r.ruleOverride) { <mat-icon class="rule" matTooltip="Raised or re-routed by a rule">bolt</mat-icon> }
              } @else { <span class="muted">–</span> }
            </td>
          </ng-container>
          <ng-container matColumnDef="category">
            <th mat-header-cell *matHeaderCellDef>Category</th>
            <td mat-cell *matCellDef="let r">{{ r.category ? categoryLabel[r.category] : '' }}</td>
          </ng-container>
          <ng-container matColumnDef="text">
            <th mat-header-cell *matHeaderCellDef>Ticket</th>
            <td mat-cell *matCellDef="let r" class="preview">
              {{ r.preview }}
              @if (r.triageError) { <div class="err">triage failed: {{ r.triageError }}</div> }
            </td>
          </ng-container>
          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef>Status</th>
            <td mat-cell *matCellDef="let r">
              <span class="chip">{{ r.status }}</span>
              @if (r.status === 'NEW' && !r.triageError) { <mat-progress-bar mode="indeterminate" class="mini" /> }
            </td>
          </ng-container>
          <ng-container matColumnDef="human">
            <th mat-header-cell *matHeaderCellDef>Human</th>
            <td mat-cell *matCellDef="let r">
              @if (r.needsHuman) { <mat-icon class="human" matTooltip="Needs a human">person</mat-icon> }
            </td>
          </ng-container>
          <ng-container matColumnDef="age">
            <th mat-header-cell *matHeaderCellDef>Age</th>
            <td mat-cell *matCellDef="let r" class="muted">{{ age(r.createdAt, now()) }}</td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let r; columns: columns" (click)="open(r)" class="clickable"></tr>
          <tr class="mat-row" *matNoDataRow>
            <td class="mat-cell empty muted" [attr.colspan]="columns.length">No tickets. Send one from the widget or the “New ticket” button.</td>
          </tr>
        </table>
        <mat-paginator [length]="total()" [pageIndex]="page()" [pageSize]="size()" [pageSizeOptions]="[25, 50, 100]"
                       (page)="onPage($event)" />
      </div>
    </div>
  `,
  styles: `
    .head h1 { margin: 0; }
    .head { margin-bottom: 12px; }
    .stat { font-size: 13px; }
    .filters { margin-bottom: 12px; }
    .arms { margin-bottom: 12px; font-size: 13px; }
    .filters mat-form-field { width: 170px; }
    .filters .narrow { width: 120px; }
    .table-wrap { overflow-x: auto; border: 1px solid var(--mat-sys-outline-variant); border-radius: 8px; }
    table { width: 100%; }
    .preview { max-width: 520px; }
    .clickable { cursor: pointer; }
    .clickable:hover { background: var(--mat-sys-surface-container-low); }
    .rule { font-size: 18px; width: 18px; height: 18px; vertical-align: middle; color: #b26a00; }
    .human { color: var(--mat-sys-primary); }
    .mini { width: 60px; margin-top: 4px; }
    .err { color: #a1180f; font-size: 12px; }
    .empty { padding: 24px; text-align: center; }
  `,
})
export class QueuePage implements OnInit {
  private readonly api = inject(Api);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly statuses = STATUSES;
  protected readonly categories = CATEGORIES;
  protected readonly priorities = PRIORITIES;
  protected readonly categoryLabel: Record<string, string> = CATEGORY_LABEL;
  protected readonly age = age;
  protected readonly columns = ['id', 'priority', 'category', 'text', 'status', 'human', 'age'];

  protected readonly status = signal('');
  protected readonly category = signal('');
  protected readonly priority = signal('');
  protected readonly humanOnly = signal(false);
  protected readonly sort = signal('priority');
  protected readonly page = signal(0);
  protected readonly size = signal(25);

  protected readonly rows = signal<TicketRow[]>([]);
  protected readonly total = signal(0);
  protected readonly stats = signal<Stats | null>(null);
  protected readonly loading = signal(false);
  protected readonly now = signal(Date.now());
  protected readonly showNew = signal(false);
  protected readonly armRows = computed(() =>
    Object.entries(this.stats()?.arms ?? {}).map(([name, s]) => ({ name, s })),
  );

  ngOnInit(): void {
    this.reload();
    const timer = setInterval(() => this.reload(true), REFRESH_MS);
    this.destroyRef.onDestroy(() => clearInterval(timer));
  }

  protected reload(background = false): void {
    if (!background) {
      this.loading.set(true);
    }
    this.api
      .tickets({
        status: this.status(),
        category: this.category(),
        priority: this.priority(),
        needsHuman: this.humanOnly() ? true : null,
        sort: this.sort(),
        page: this.page(),
        size: this.size(),
      })
      .subscribe({
        next: (p) => {
          this.rows.set(p.items);
          this.total.set(p.total);
          this.now.set(Date.now());
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
    this.api.stats().subscribe((s) => this.stats.set(s));
  }

  protected onPage(e: PageEvent): void {
    this.page.set(e.pageIndex);
    this.size.set(e.pageSize);
    this.reload();
  }

  protected open(r: TicketRow): void {
    this.router.navigate(['/admin/tickets', r.id]);
  }

  protected onCreated(id: number): void {
    this.showNew.set(false);
    this.reload();
    this.router.navigate(['/admin/tickets', id]);
  }
}
