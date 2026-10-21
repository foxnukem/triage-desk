import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';

import { Api } from '../core/api';
import { json } from '../core/format';
import { Sample, TriageResult } from '../core/models';
import { ReplyView } from './reply-view';
import { TraceTimeline } from './trace-timeline';
import { TriageCard } from './triage-card';

/** Triage any text without persisting it, with the pipeline switches exposed. */
@Component({
  selector: 'app-playground',
  imports: [
    FormsModule, MatButtonModule, MatButtonToggleModule, MatTooltipModule, MatCheckboxModule, MatIconModule, MatInputModule, MatSelectModule, MatProgressBarModule,
    TriageCard, ReplyView, TraceTimeline,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <h1>Playground</h1>
      <div class="cols">
        <div>
          <div class="row">
            <mat-form-field class="grow" subscriptSizing="dynamic">
              <mat-label>Sample ticket</mat-label>
              <mat-select [ngModel]="sampleId()" (ngModelChange)="pick($event)">
                <mat-option value="">— write your own —</mat-option>
                @for (s of orderedSamples(); track s.id) {
                  <mat-option [value]="s.id">
                    @if (s.demo) { ★{{ s.demo }} · }{{ s.id }} · {{ s.slice }} · {{ s.tags.join(', ') }}
                  </mat-option>
                }
              </mat-select>
            </mat-form-field>
          </div>
          <mat-form-field class="full">
            <mat-label>Ticket text</mat-label>
            <textarea matInput rows="7" maxlength="2000" [(ngModel)]="text"></textarea>
          </mat-form-field>
          @if (sample(); as s) {
            <details class="expected" open>
              <summary class="muted">Expected (dataset label)</summary>
              <pre class="mono pre">{{ json(s.expected) }}</pre>
            </details>
          }
          <div class="row switches">
            <mat-checkbox [(ngModel)]="ruleRouter" [disabled]="megaPrompt()">Rule router</mat-checkbox>
            <mat-checkbox [(ngModel)]="validation" [disabled]="megaPrompt()">Validation + retries</mat-checkbox>
            <mat-checkbox [(ngModel)]="critic" [disabled]="megaPrompt()">Critic</mat-checkbox>
            <mat-checkbox [(ngModel)]="megaPrompt">Mega-prompt baseline</mat-checkbox>
          </div>
          <div class="row switches">
            <span class="muted">Classifier</span>
            <mat-button-toggle-group [(ngModel)]="classifier" [disabled]="megaPrompt()" aria-label="Classifier">
              <mat-button-toggle value="llm">LLM</mat-button-toggle>
              <mat-button-toggle value="laya" [disabled]="!classifiers().includes('laya')"
                                 [matTooltip]="classifiers().includes('laya') ? 'Encoder classifier, one forward pass' : 'Laya model not installed'">
                Laya</mat-button-toggle>
            </mat-button-toggle-group>
          </div>
          <div class="row">
            <mat-form-field subscriptSizing="dynamic">
              <mat-label>Model</mat-label>
              <mat-select [(ngModel)]="model">
                <mat-option value="">default</mat-option>
                @for (m of models(); track m) { <mat-option [value]="m">{{ m }}</mat-option> }
              </mat-select>
            </mat-form-field>
            <button mat-flat-button [disabled]="running() || !text().trim()" (click)="run()">
              <mat-icon>play_arrow</mat-icon> Triage</button>
            @if (running()) { <span class="muted">running {{ elapsed() }} s… (CPU-only inference can take minutes)</span> }
          </div>
          @if (running()) { <mat-progress-bar mode="indeterminate" class="progress" /> }
          @if (error()) { <p class="err">{{ error() }}</p> }

          @if (result(); as r) {
            <h2>Result</h2>
            <app-triage-card [result]="r" />
            @if (r.reply; as d) {
              <h2>Draft reply</h2>
              <app-reply-view [draft]="d" [lint]="r.diagnostics?.policyLintFinal ?? []" />
            }
          }
        </div>
        <aside>
          <h2>Trace</h2>
          @if (result(); as r) {
            <app-trace-timeline [trace]="r.trace" />
          } @else {
            <p class="muted">Run a ticket to see every step: rules, LLM calls and their retries, tool calls, the critic.</p>
          }
        </aside>
      </div>
    </div>
  `,
  styles: `
    .cols { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 24px; }
    .full { width: 100%; margin-top: 12px; }
    .switches { margin: 4px 0 12px; }
    .expected { margin-bottom: 8px; font-size: 13px; }
    .expected pre { margin: 4px 0 0; padding: 8px; background: var(--mat-sys-surface-container-low); border-radius: 6px; }
    .progress { margin-top: 8px; }
    h2 { font: var(--mat-sys-title-medium); margin: 20px 0 8px; }
    aside h2 { margin-top: 0; }
    .err { color: #a1180f; }
    @media (max-width: 1100px) { .cols { grid-template-columns: minmax(0, 1fr); } }
  `,
})
export class PlaygroundPage implements OnInit {
  private readonly api = inject(Api);
  protected readonly json = json;

  protected readonly samples = signal<Sample[]>([]);
  protected readonly orderedSamples = computed(() =>
    [...this.samples()].sort((a, b) => (a.demo ?? 99) - (b.demo ?? 99) || a.id.localeCompare(b.id)),
  );
  protected readonly sampleId = signal('');
  protected readonly sample = computed(() => this.samples().find((s) => s.id === this.sampleId()));
  protected readonly models = signal<string[]>([]);

  protected readonly text = signal('');
  protected readonly model = signal('');
  protected readonly ruleRouter = signal(true);
  protected readonly validation = signal(true);
  protected readonly critic = signal(true);
  protected readonly megaPrompt = signal(false);
  protected readonly classifier = signal<'llm' | 'laya'>('llm');
  protected readonly classifiers = signal<string[]>(['llm']);

  protected readonly running = signal(false);
  protected readonly elapsed = signal(0);
  protected readonly result = signal<TriageResult | null>(null);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.api.samples().subscribe((s) => this.samples.set(s));
    this.api.models().subscribe({ next: (m) => this.models.set(m), error: () => this.models.set([]) });
    this.api.defaults().subscribe(({ defaults, classifiers }) => {
      this.classifiers.set(classifiers ?? ['llm']);
      this.ruleRouter.set(defaults.ruleRouter);
      this.validation.set(defaults.validation);
      this.critic.set(defaults.critic);
    });
  }

  protected pick(id: string): void {
    this.sampleId.set(id);
    const s = this.samples().find((x) => x.id === id);
    if (s) {
      this.text.set(s.text);
    }
  }

  protected run(): void {
    this.running.set(true);
    this.error.set(null);
    this.result.set(null);
    this.elapsed.set(0);
    const started = Date.now();
    const timer = setInterval(() => this.elapsed.set(Math.round((Date.now() - started) / 1000)), 1000);
    this.api
      .triage(this.text(), {
        model: this.model() || undefined,
        ruleRouter: this.ruleRouter(),
        validation: this.validation(),
        critic: this.critic(),
        megaPrompt: this.megaPrompt(),
        classifier: this.classifier(),
      })
      .subscribe({
        next: (r) => {
          clearInterval(timer);
          this.result.set(r);
          this.running.set(false);
        },
        error: (e) => {
          clearInterval(timer);
          this.error.set(e?.error?.error ?? `Request failed (${e?.status ?? 'network'})`);
          this.running.set(false);
        },
      });
  }
}
