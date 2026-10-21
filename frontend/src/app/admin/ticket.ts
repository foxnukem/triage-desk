import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';

import { Api } from '../core/api';
import { DecisionAction, TicketDetail } from '../core/models';
import { ReplyView } from './reply-view';
import { TraceTimeline } from './trace-timeline';
import { TriageCard } from './triage-card';

const POLL_MS = 3000;

@Component({
  selector: 'app-ticket',
  imports: [
    DatePipe, FormsModule, RouterLink, MatButtonModule, MatIconModule, MatInputModule, MatSelectModule, MatProgressBarModule,
    TriageCard, ReplyView, TraceTimeline,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <a routerLink="/admin/queue" class="back">← Queue</a>
      @if (t(); as t) {
        <div class="row title">
          <h1>Ticket #{{ t.id }}</h1>
          <span class="chip">{{ t.status }}</span>
          <span class="muted">{{ t.source }} · {{ t.customerEmail ?? 'no email' }} · {{ t.createdAt | date: 'short' }}</span>
        </div>

        <div class="cols">
          <div class="main">
            <section>
              <h2>Customer message</h2>
              <div class="pre msg">{{ t.text }}</div>
            </section>

            @if (t.status === 'NEW' && !t.triageError) {
              <section>
                <h2>Triage</h2>
                <mat-progress-bar mode="indeterminate" />
                <p class="muted">Triage is running on the local model; this takes from seconds to minutes. The card updates by itself.</p>
              </section>
            }
            @if (t.triageError) {
              <section class="failed">
                <h2>Triage failed</h2>
                <p class="mono">{{ t.triageError }}</p>
              </section>
            }

            @if (t.result; as r) {
              <section>
                <h2>Triage</h2>
                <app-triage-card [result]="r" />
              </section>

              <section>
                <h2>Draft reply</h2>
                @if (r.reply; as d) {
                  @if (editing()) {
                    <mat-form-field class="full">
                      <mat-label>Final reply</mat-label>
                      <textarea matInput rows="8" [(ngModel)]="editText"></textarea>
                    </mat-form-field>
                  } @else {
                    <app-reply-view [draft]="d" [lint]="r.diagnostics?.policyLintFinal ?? []" />
                    @if (r.diagnostics?.draftBeforeCritic; as before) {
                      @if (before.text !== d.text) {
                        <details class="before">
                          <summary class="muted">Draft before the critic rewrote it</summary>
                          <div class="pre">{{ before.text }}</div>
                        </details>
                      }
                    }
                  }
                } @else {
                  <p class="muted">No draft (the reply step was off or failed).</p>
                }
              </section>

              @if (!closed()) {
                <div class="row actions">
                  @if (editing()) {
                    <button mat-flat-button [disabled]="busy() || !editText().trim()" (click)="decide('EDITED')">Save and accept</button>
                    <button mat-button (click)="editing.set(false)">Cancel</button>
                  } @else {
                    <button mat-flat-button [disabled]="busy() || !r.reply" (click)="decide('ACCEPTED')">
                      <mat-icon>check</mat-icon> Accept</button>
                    <button mat-stroked-button [disabled]="busy() || !r.reply" (click)="startEdit()">
                      <mat-icon>edit</mat-icon> Edit and accept</button>
                    <button mat-stroked-button [disabled]="busy()" (click)="decide('ESCALATED')">
                      <mat-icon>priority_high</mat-icon> Escalate</button>
                    <button mat-button [disabled]="busy()" (click)="decide('REJECTED')">Reject draft</button>
                  }
                </div>
              }
            }

            <div class="row actions retriage">
              <mat-form-field subscriptSizing="dynamic">
                <mat-label>Model</mat-label>
                <mat-select [(ngModel)]="model">
                  <mat-option value="">default</mat-option>
                  @for (m of models(); track m) { <mat-option [value]="m">{{ m }}</mat-option> }
                </mat-select>
              </mat-form-field>
              <mat-form-field subscriptSizing="dynamic">
                <mat-label>Classifier</mat-label>
                <mat-select [(ngModel)]="classifier">
                  <mat-option value="">ticket's A/B arm</mat-option>
                  @for (c of classifiers(); track c) { <mat-option [value]="c">{{ c === 'laya' ? 'Laya' : 'LLM' }}</mat-option> }
                </mat-select>
              </mat-form-field>
              <button mat-stroked-button [disabled]="busy() || t.status === 'NEW'" (click)="retriage()">
                <mat-icon>refresh</mat-icon> Re-triage</button>
            </div>

            @if (t.decisions.length) {
              <section>
                <h2>Decisions</h2>
                <ul class="list">
                  @for (d of t.decisions; track d.id) {
                    <li><span class="chip">{{ d.action }}</span> {{ d.decidedAt | date: 'short' }}
                      @if (d.note) { — {{ d.note }} }
                      @if (d.action === 'EDITED') { <div class="pre muted small">{{ d.finalText }}</div> }
                    </li>
                  }
                </ul>
              </section>
            }
            @if (t.history.length > 1) {
              <section>
                <h2>Triage history</h2>
                <ul class="list">
                  @for (h of t.history; track h.id) {
                    <li [class.current]="h.id === t.currentResultId">
                      <span class="mono">{{ h.model }}</span> → {{ h.category }} {{ h.priority }}
                      {{ h.needsHuman ? '· needs human' : '' }} <span class="muted">{{ h.createdAt | date: 'short' }}</span>
                    </li>
                  }
                </ul>
              </section>
            }
          </div>

          <aside class="side">
            <h2>Trace</h2>
            @if (t.result; as r) {
              <app-trace-timeline [trace]="r.trace" />
            } @else {
              <p class="muted">No trace yet.</p>
            }
          </aside>
        </div>
      } @else if (notFound()) {
        <p>Ticket not found.</p>
      } @else {
        <mat-progress-bar mode="indeterminate" />
      }
    </div>
  `,
  styles: `
    .back { color: var(--mat-sys-primary); text-decoration: none; font-size: 14px; }
    .title { margin: 8px 0 16px; }
    .title h1 { margin: 0; }
    h2 { font: var(--mat-sys-title-medium); margin: 0 0 8px; }
    section { margin-bottom: 20px; }
    .cols { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 24px; }
    .msg { padding: 12px 14px; border-left: 3px solid var(--mat-sys-primary); background: var(--mat-sys-surface-container-low); }
    .failed { color: #a1180f; }
    .full { width: 100%; }
    .actions { margin-bottom: 20px; }
    .retriage mat-form-field { width: 200px; }
    .before { margin-top: 8px; font-size: 13px; }
    .list { padding-left: 18px; margin: 0; }
    .list li { margin-bottom: 6px; }
    .list li.current { font-weight: 600; }
    .small { font-size: 12px; }
    @media (max-width: 1100px) { .cols { grid-template-columns: minmax(0, 1fr); } }
  `,
})
export class TicketPage implements OnInit {
  /** Route parameter (component input binding). */
  readonly id = input.required<string>();

  private readonly api = inject(Api);
  private readonly snack = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly t = signal<TicketDetail | null>(null);
  protected readonly notFound = signal(false);
  protected readonly busy = signal(false);
  protected readonly editing = signal(false);
  protected readonly editText = signal('');
  protected readonly models = signal<string[]>([]);
  protected readonly model = signal('');
  protected readonly classifier = signal('');
  protected readonly classifiers = signal<string[]>(['llm']);

  constructor() {
    effect(() => {
      this.id();
      this.t.set(null);
      this.load();
    });
  }

  ngOnInit(): void {
    this.api.models().subscribe({ next: (m) => this.models.set(m), error: () => this.models.set([]) });
    this.api.defaults().subscribe(({ classifiers }) => this.classifiers.set(classifiers ?? ['llm']));
    // Poll while triage is pending.
    const timer = setInterval(() => {
      if (this.t()?.status === 'NEW' && !this.t()?.triageError) {
        this.load();
      }
    }, POLL_MS);
    this.destroyRef.onDestroy(() => clearInterval(timer));
  }

  protected closed(): boolean {
    const s = this.t()?.status;
    return s === 'RESOLVED' || s === 'ESCALATED';
  }

  private load(): void {
    this.api.ticket(Number(this.id())).subscribe({
      next: (t) => this.t.set(t),
      error: () => this.notFound.set(true),
    });
  }

  protected startEdit(): void {
    this.editText.set(this.t()?.result?.reply?.text ?? '');
    this.editing.set(true);
  }

  protected decide(action: DecisionAction): void {
    this.busy.set(true);
    const text = action === 'EDITED' ? this.editText() : undefined;
    this.api.decide(Number(this.id()), action, text).subscribe({
      next: (t) => {
        this.t.set(t);
        this.busy.set(false);
        this.editing.set(false);
        this.snack.open(`Decision saved: ${action.toLowerCase()}`, undefined, { duration: 2500 });
      },
      error: (e) => {
        this.busy.set(false);
        this.snack.open(e?.error?.error ?? 'Could not save the decision', 'OK');
      },
    });
  }

  protected retriage(): void {
    this.busy.set(true);
    this.api.retriage(Number(this.id()), this.model(), this.classifier()).subscribe({
      next: () => {
        this.busy.set(false);
        this.load();
        this.snack.open('Re-triage queued', undefined, { duration: 2500 });
      },
      error: () => this.busy.set(false),
    });
  }
}
