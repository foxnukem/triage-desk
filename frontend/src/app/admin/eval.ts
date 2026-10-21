import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';

import { Api } from '../core/api';
import { CATEGORIES, CaseRow, EvalConfig, Metrics, RunDetail, RunSummary } from '../core/models';
import { TraceTimeline } from './trace-timeline';

interface Headline {
  key: string;
  label: string;
  kind: 'pct' | 'sec' | 'num';
  lowerIsBetter?: boolean;
}

/** Same rows as the ablation table in eval/reports (ReportWriter.HEADLINE). */
const HEADLINE: Headline[] = [
  { key: 'categoryAccuracy', label: 'Category accuracy', kind: 'pct' },
  { key: 'categoryMacroF1', label: 'Category macro-F1', kind: 'pct' },
  { key: 'priorityExact', label: 'Priority exact', kind: 'pct' },
  { key: 'priorityWithin1', label: 'Priority within-1', kind: 'pct' },
  { key: 'p1Recall', label: 'P1 recall', kind: 'pct' },
  { key: 'p1p2RecallNoLowerThanP2', label: 'P1/P2 kept ≥ P2', kind: 'pct' },
  { key: 'entities.precision', label: 'Entity precision', kind: 'pct' },
  { key: 'entities.recall', label: 'Entity recall', kind: 'pct' },
  { key: 'hallucinatedEntitiesFirstAttempt', label: 'Hallucinated entities (1st attempt)', kind: 'pct', lowerIsBetter: true },
  { key: 'hallucinatedEntitiesFinal', label: 'Hallucinated entities (final)', kind: 'pct', lowerIsBetter: true },
  { key: 'schemaValidFirstAttempt', label: 'All steps valid on 1st attempt', kind: 'pct' },
  { key: 'schemaValidAfterRetries', label: 'All steps valid after retries', kind: 'pct' },
  { key: 'escalationPrecision', label: 'Escalation precision', kind: 'pct' },
  { key: 'escalationRecall', label: 'Escalation recall', kind: 'pct' },
  { key: 'citationHitRate', label: 'Citation hit rate', kind: 'pct' },
  { key: 'policyViolationRate', label: 'Drafts violating policy (lint)', kind: 'pct', lowerIsBetter: true },
  { key: 'sentimentAccuracyLabelled', label: 'Sentiment (labelled cases)', kind: 'pct' },
  { key: 'multiIntentRecall', label: 'Multi-intent detected', kind: 'pct' },
  { key: 'latencyP50Ms', label: 'Latency p50', kind: 'sec', lowerIsBetter: true },
  { key: 'latencyP95Ms', label: 'Latency p95', kind: 'sec', lowerIsBetter: true },
  { key: 'llmCallsPerCase', label: 'LLM calls per ticket', kind: 'num', lowerIsBetter: true },
];

const ABBR: Record<string, string> = { RETURN_REFUND: 'RET', TECHNICAL: 'TECH', SHIPPING: 'SHIP', BILLING: 'BILL', ACCOUNT: 'ACC', OTHER: 'OTHER' };

function get(m: Metrics | undefined, dotted: string): unknown {
  return dotted.split('.').reduce<any>((cur, k) => (cur && typeof cur === 'object' ? cur[k] : undefined), m);
}

function presetConfig(name: string, model: string): EvalConfig {
  const base: EvalConfig = { label: name, model, ruleRouter: true, validation: true, critic: true, megaPrompt: false, reply: true };
  switch (name) {
    case 'no-router': return { ...base, ruleRouter: false };
    case 'no-validation': return { ...base, validation: false };
    case 'no-critic': return { ...base, critic: false };
    case 'mega-prompt': return { ...base, ruleRouter: false, validation: false, critic: false, megaPrompt: true };
    case 'laya': return { ...base, classifier: 'laya' };
    default: return base;
  }
}

@Component({
  selector: 'app-eval',
  imports: [
    DatePipe, FormsModule, MatButtonModule, MatCheckboxModule, MatIconModule, MatInputModule, MatProgressBarModule,
    MatSelectModule, MatSlideToggleModule, TraceTimeline,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <h1>Evaluation</h1>

      <section class="box">
        <div class="row">
          <mat-form-field subscriptSizing="dynamic">
            <mat-label>Preset</mat-label>
            <mat-select [ngModel]="preset()" (ngModelChange)="applyPreset($event)">
              @for (p of presets(); track p) { <mat-option [value]="p">{{ p }}</mat-option> }
            </mat-select>
          </mat-form-field>
          <mat-form-field subscriptSizing="dynamic">
            <mat-label>Model</mat-label>
            <mat-select [(ngModel)]="model">
              @for (m of models(); track m) { <mat-option [value]="m">{{ m }}</mat-option> }
            </mat-select>
          </mat-form-field>
          <mat-form-field subscriptSizing="dynamic" class="narrow">
            <mat-label>Label</mat-label>
            <input matInput [(ngModel)]="label" />
          </mat-form-field>
          <mat-form-field subscriptSizing="dynamic" class="narrow">
            <mat-label>Slice</mat-label>
            <mat-select [(ngModel)]="slice">
              <mat-option value="">all 50</mat-option>
              <mat-option value="standard">standard</mat-option>
              <mat-option value="hard">hard</mat-option>
              <mat-option value="adversarial">adversarial</mat-option>
            </mat-select>
          </mat-form-field>
          <mat-form-field subscriptSizing="dynamic" class="tiny">
            <mat-label>Limit</mat-label>
            <input matInput type="number" min="1" [(ngModel)]="limit" />
          </mat-form-field>
        </div>
        <div class="row">
          <mat-checkbox [(ngModel)]="ruleRouter" [disabled]="megaPrompt()">Rule router</mat-checkbox>
          <mat-checkbox [(ngModel)]="validation" [disabled]="megaPrompt()">Validation</mat-checkbox>
          <mat-checkbox [(ngModel)]="critic" [disabled]="megaPrompt()">Critic</mat-checkbox>
          <mat-checkbox [(ngModel)]="megaPrompt">Mega-prompt</mat-checkbox>
          <mat-checkbox [(ngModel)]="reply">Reply step</mat-checkbox>
          <mat-checkbox [(ngModel)]="useLaya" [disabled]="megaPrompt() || !classifiers().includes('laya')">Laya classifier</mat-checkbox>
          <span class="grow"></span>
          <button mat-flat-button [disabled]="starting() || running()" (click)="start()"><mat-icon>play_arrow</mat-icon> Start run</button>
        </div>
        @if (running()) { <p class="muted note">A run is in progress; runs execute one at a time.</p> }
      </section>

      <div class="cols">
        <section class="runs">
          <h2>Runs</h2>
          @for (r of runs(); track r.id) {
            <button type="button" class="run" [class.sel]="r.id === selectedId()" (click)="select(r.id)">
              <div class="row">
                <b>#{{ r.id }} {{ r.label }}</b>
                <span class="chip" [class.good]="r.status === 'DONE'" [class.bad]="r.status === 'FAILED'">{{ r.status }}</span>
              </div>
              <div class="muted small mono">{{ r.config.model }}</div>
              <div class="muted small">{{ r.startedAt | date: 'short' }} · {{ r.doneCases }}/{{ r.totalCases }}
                @if (r.metrics) { · acc {{ fmt(get(r.metrics, 'categoryAccuracy'), 'pct') }} }</div>
              @if (r.status === 'RUNNING') {
                <mat-progress-bar mode="determinate" [value]="(100 * r.doneCases) / (r.totalCases || 1)" />
              }
            </button>
          } @empty {
            <p class="muted">No runs yet.</p>
          }
        </section>

        <section class="detail">
          @if (detail(); as d) {
            <div class="row">
              <h2>Run #{{ d.run.id }} · {{ d.run.label }}</h2>
              <span class="grow"></span>
              <mat-form-field subscriptSizing="dynamic">
                <mat-label>Compare with</mat-label>
                <mat-select [ngModel]="compareId()" (ngModelChange)="compareId.set($event)">
                  <mat-option [value]="null">—</mat-option>
                  @for (r of doneRuns(); track r.id) {
                    @if (r.id !== d.run.id) { <mat-option [value]="r.id">#{{ r.id }} {{ r.label }}</mat-option> }
                  }
                </mat-select>
              </mat-form-field>
            </div>
            <p class="muted small">
              {{ d.run.config.model }} · router {{ onOff(d.run.config.ruleRouter) }} · validation {{ onOff(d.run.config.validation) }}
              · critic {{ onOff(d.run.config.critic) }} {{ d.run.config.megaPrompt ? '· mega-prompt' : '' }}
              {{ d.run.config.classifier === 'laya' ? '· classifier Laya' : '' }}
              {{ d.run.config.reply ? '' : '· reply off' }}
              @if (d.run.error) { · <span class="err">{{ d.run.error }}</span> }
            </p>

            @if (d.run.metrics; as m) {
              <table class="metrics">
                <thead><tr><th>Metric</th><th>This run</th>@if (compare(); as c) { <th>#{{ c.id }} {{ c.label }}</th><th>Δ</th> }</tr></thead>
                <tbody>
                  @for (h of headline; track h.key) {
                    <tr>
                      <td>{{ h.label }}</td>
                      <td class="num">{{ fmt(get(m, h.key), h.kind) }}</td>
                      @if (compare(); as c) {
                        <td class="num muted">{{ fmt(get(c.metrics, h.key), h.kind) }}</td>
                        <td class="num" [class]="deltaClass(h, m, c.metrics)">{{ delta(h, m, c.metrics) }}</td>
                      }
                    </tr>
                  }
                </tbody>
              </table>
              <p class="muted small">
                Before rule overrides: category {{ fmt(get(m, 'beforeRuleOverrides.categoryAccuracy'), 'pct') }},
                P1 recall {{ fmt(get(m, 'beforeRuleOverrides.p1Recall'), 'pct') }}
                ({{ get(m, 'beforeRuleOverrides.overrides') ?? 0 }} overrides).
                Drafts violating policy before the critic: {{ fmt(get(m, 'policyViolationRateBeforeCritic'), 'pct') }}.
              </p>

              <div class="row grids">
                <div>
                  <h3>Category confusion <span class="muted small">(rows = expected)</span></h3>
                  <table class="confusion">
                    <tr><th></th>@for (c of categories; track c) { <th>{{ abbr[c] }}</th> }</tr>
                    @for (e of categories; track e) {
                      <tr>
                        <th>{{ abbr[e] }}</th>
                        @for (p of categories; track p) {
                          @let n = m['confusion']?.[e]?.[p] ?? 0;
                          <td [class.diag]="e === p && n" [class.off]="e !== p && n">{{ n || '·' }}</td>
                        }
                      </tr>
                    }
                  </table>
                </div>
                <div>
                  <h3>Confidence calibration</h3>
                  <table class="confusion">
                    <tr><th>Confidence</th><th>Cases</th><th>Accuracy</th></tr>
                    @for (b of m['confidenceBins'] ?? []; track $index) {
                      <tr><td>{{ b.from }}–{{ b.to }}</td><td>{{ b.cases }}</td><td>{{ fmt(b.categoryAccuracy, 'pct') }}</td></tr>
                    }
                  </table>
                </div>
              </div>
            } @else {
              <mat-progress-bar mode="determinate" [value]="(100 * d.run.doneCases) / (d.run.totalCases || 1)" />
              <p class="muted">{{ d.run.doneCases }} of {{ d.run.totalCases }} cases done. Metrics appear when the run finishes.</p>
            }

            <div class="row">
              <h3>Cases</h3>
              <span class="grow"></span>
              <mat-slide-toggle [(ngModel)]="errorsOnly">Errors only</mat-slide-toggle>
            </div>
            <table class="cases">
              <thead><tr><th>Case</th><th>Slice</th><th>Expected</th><th>Actual</th><th>Human exp/act</th><th>Latency</th></tr></thead>
              <tbody>
                @for (c of visibleCases(); track c.caseId) {
                  <tr class="clickable" [class.wrong]="!c.correct" (click)="toggle(c.caseId)">
                    <td class="mono">{{ c.caseId }}</td>
                    <td>{{ c.slice }}</td>
                    <td>{{ c.detail.expected['category'] }} {{ c.detail.expected['priority'] }}</td>
                    <td>
                      @if (c.detail.error) { <span class="err">{{ c.detail.error }}</span> }
                      @else { {{ c.detail.actual?.['category'] }} {{ c.detail.actual?.['priority'] }}
                        @if (c.detail.result?.ruleOverride) { <mat-icon inline title="rule override">bolt</mat-icon> } }
                    </td>
                    <td>{{ c.detail.expected['needsHuman'] ?? '–' }} / {{ c.detail.actual?.['needsHuman'] ?? '–' }}</td>
                    <td class="num">{{ (c.latencyMs / 1000).toFixed(1) }} s</td>
                  </tr>
                  @if (openCase() === c.caseId) {
                    <tr class="expanded"><td colspan="6">
                      <div class="pre case-text">{{ c.detail.text }}</div>
                      @if (c.detail.actual?.['reply']; as reply) {
                        <p class="small"><b>Draft:</b> <span class="pre">{{ reply }}</span></p>
                        <p class="small muted">cited {{ (c.detail.actual?.['citedArticles'] ?? []).join(', ') || '–' }}
                          · expected {{ (c.detail.expected['relevantArticles'] ?? []).join(', ') || '–' }}</p>
                      }
                      @if (c.detail.result; as r) { <app-trace-timeline [trace]="r.trace" /> }
                    </td></tr>
                  }
                }
              </tbody>
            </table>
          } @else {
            <p class="muted">Select a run.</p>
          }
        </section>
      </div>
    </div>
  `,
  styles: `
    .box { border: 1px solid var(--mat-sys-outline-variant); border-radius: 8px; padding: 12px; margin-bottom: 20px; display: grid; gap: 10px; }
    .box mat-form-field { width: 180px; }
    .box .narrow { width: 140px; }
    .box .tiny { width: 90px; }
    .note { margin: 0; font-size: 13px; }
    .cols { display: grid; grid-template-columns: 260px minmax(0, 1fr); gap: 24px; }
    h2 { font: var(--mat-sys-title-medium); margin: 0 0 8px; }
    h3 { font: var(--mat-sys-title-small); margin: 16px 0 6px; }
    .run { display: block; width: 100%; text-align: left; background: transparent; border: 1px solid var(--mat-sys-outline-variant);
           border-radius: 8px; padding: 8px 10px; margin-bottom: 8px; cursor: pointer; font: inherit; color: inherit; }
    .run.sel { border-color: var(--mat-sys-primary); background: var(--mat-sys-surface-container-low); }
    .small { font-size: 12px; }
    table { border-collapse: collapse; font-size: 13px; }
    th, td { padding: 4px 8px; border-bottom: 1px solid var(--mat-sys-outline-variant); text-align: left; }
    .num { text-align: right; font-variant-numeric: tabular-nums; }
    .metrics { min-width: 420px; }
    .better { color: #1c6b34; font-weight: 600; }
    .worse { color: #a1180f; font-weight: 600; }
    .grids { align-items: flex-start; gap: 32px; }
    .confusion td, .confusion th { text-align: center; }
    .confusion td.diag { background: #dff3e4; }
    .confusion td.off { background: #fde2e1; }
    .cases { width: 100%; }
    .clickable { cursor: pointer; }
    .wrong td:first-child { font-weight: 700; color: #a1180f; }
    .expanded td { background: var(--mat-sys-surface-container-lowest); }
    .case-text { padding: 8px 0; }
    .err { color: #a1180f; }
    mat-icon[inline] { vertical-align: -2px; color: #b26a00; }
    @media (max-width: 1000px) { .cols { grid-template-columns: minmax(0, 1fr); } .detail { overflow-x: auto; } }
  `,
})
export class EvalPage implements OnInit {
  private readonly api = inject(Api);
  private readonly snack = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly headline = HEADLINE;
  protected readonly categories = CATEGORIES;
  protected readonly abbr = ABBR;
  protected readonly get = get;

  protected readonly presets = signal<string[]>(['full']);
  protected readonly models = signal<string[]>([]);
  protected readonly preset = signal('full');
  protected readonly model = signal('');
  protected readonly label = signal('full');
  protected readonly slice = signal('');
  protected readonly limit = signal<number | null>(null);
  protected readonly ruleRouter = signal(true);
  protected readonly validation = signal(true);
  protected readonly critic = signal(true);
  protected readonly megaPrompt = signal(false);
  protected readonly reply = signal(true);
  protected readonly useLaya = signal(false);
  protected readonly classifiers = signal<string[]>(['llm']);
  protected readonly starting = signal(false);

  protected readonly runs = signal<RunSummary[]>([]);
  protected readonly doneRuns = computed(() => this.runs().filter((r) => r.status === 'DONE'));
  protected readonly running = computed(() => this.runs().some((r) => r.status === 'RUNNING'));
  protected readonly selectedId = signal<number | null>(null);
  protected readonly detail = signal<RunDetail | null>(null);
  protected readonly compareId = signal<number | null>(null);
  protected readonly compare = computed(() => this.runs().find((r) => r.id === this.compareId() && r.metrics));
  protected readonly errorsOnly = signal(false);
  protected readonly openCase = signal<string | null>(null);
  protected readonly visibleCases = computed<CaseRow[]>(() => {
    const cases = this.detail()?.cases ?? [];
    return this.errorsOnly() ? cases.filter((c) => !c.correct || c.detail.error) : cases;
  });

  ngOnInit(): void {
    this.api.evalPresets().subscribe((p) => this.presets.set(p));
    this.api.defaults().subscribe(({ defaults, classifiers }) => {
      this.classifiers.set(classifiers ?? ['llm']);
      if (!this.model()) {
        this.model.set(defaults.model);
      }
    });
    this.api.models().subscribe({ next: (m) => this.models.set(m), error: () => this.models.set([]) });
    this.refresh();
    const timer = setInterval(() => this.running() && this.refresh(), 5000);
    this.destroyRef.onDestroy(() => clearInterval(timer));
  }

  private refresh(): void {
    this.api.evalRuns().subscribe((runs) => {
      this.runs.set(runs);
      const sel = this.selectedId() ?? runs[0]?.id ?? null;
      if (sel !== null) {
        this.load(sel);
      }
    });
  }

  protected select(id: number): void {
    this.openCase.set(null);
    this.load(id);
  }

  private load(id: number): void {
    const switching = id !== this.selectedId();
    this.selectedId.set(id);
    this.api.evalRun(id).subscribe((d) => {
      this.detail.set(d);
      if (switching) {
        // Default comparison: the previous finished run.
        const prev = this.doneRuns().find((r) => r.id < id);
        this.compareId.set(prev?.id ?? null);
      }
    });
  }

  protected applyPreset(name: string): void {
    this.preset.set(name);
    const cfg = presetConfig(name, this.model());
    this.label.set(name);
    this.ruleRouter.set(cfg.ruleRouter);
    this.validation.set(cfg.validation);
    this.critic.set(cfg.critic);
    this.megaPrompt.set(cfg.megaPrompt);
    this.useLaya.set(cfg.classifier === 'laya');
  }

  protected start(): void {
    const cfg: EvalConfig = {
      label: this.label().trim() || 'custom',
      model: this.model(),
      ruleRouter: this.ruleRouter(),
      validation: this.validation(),
      critic: this.critic(),
      megaPrompt: this.megaPrompt(),
      reply: this.reply(),
      limit: this.limit() || null,
      slice: this.slice() || null,
      caseIds: null,
      classifier: this.useLaya() && !this.megaPrompt() ? 'laya' : 'llm',
    };
    this.starting.set(true);
    this.api.startEval(cfg).subscribe({
      next: ({ runId }) => {
        this.starting.set(false);
        this.selectedId.set(runId);
        this.refresh();
        this.snack.open(`Run #${runId} started`, undefined, { duration: 2500 });
      },
      error: (e) => {
        this.starting.set(false);
        this.snack.open(e?.error?.error ?? 'Could not start the run', 'OK');
      },
    });
  }

  protected toggle(id: string): void {
    this.openCase.set(this.openCase() === id ? null : id);
  }

  protected onOff(b: boolean): string {
    return b ? 'on' : 'off';
  }

  protected fmt(v: unknown, kind: Headline['kind']): string {
    if (typeof v !== 'number') {
      return '–';
    }
    return kind === 'pct' ? `${Math.round(v * 100)}%` : kind === 'sec' ? `${(v / 1000).toFixed(1)} s` : v.toFixed(1);
  }

  protected delta(h: Headline, m: Metrics, base: Metrics | undefined): string {
    const a = get(m, h.key);
    const b = get(base, h.key);
    if (typeof a !== 'number' || typeof b !== 'number') {
      return '';
    }
    const d = a - b;
    if (Math.abs(d) < 1e-9) {
      return '=';
    }
    const sign = d > 0 ? '+' : '−';
    const abs = Math.abs(d);
    return h.kind === 'pct' ? `${sign}${Math.round(abs * 100)} pp` : h.kind === 'sec' ? `${sign}${(abs / 1000).toFixed(1)} s` : `${sign}${abs.toFixed(1)}`;
  }

  protected deltaClass(h: Headline, m: Metrics, base: Metrics | undefined): string {
    const a = get(m, h.key);
    const b = get(base, h.key);
    if (typeof a !== 'number' || typeof b !== 'number' || Math.abs(a - b) < 1e-9) {
      return 'num';
    }
    const better = h.lowerIsBetter ? a < b : a > b;
    return better ? 'num better' : 'num worse';
  }
}
