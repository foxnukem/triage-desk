import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

import { json } from '../core/format';
import { PipelineTrace, StepStatus } from '../core/models';

const ICON: Record<StepStatus, string> = {
  OK: 'check_circle',
  INVALID: 'replay',
  ERROR: 'error',
  FAILED: 'cancel',
  OVERRIDE: 'bolt',
  INFO: 'info',
  SKIPPED: 'remove_circle_outline',
};

const LLM_STEPS = new Set(['classify', 'extract', 'reply', 'critic-review', 'critic-rewrite', 'mega-prompt']);

/**
 * Every step of the pipeline in order: deterministic steps, LLM calls with each retry,
 * the validator errors fed back, tool calls with their results, and the raw output.
 */
@Component({
  selector: 'app-trace-timeline',
  imports: [MatIconModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = trace();
    <p class="muted summary">
      {{ t.steps.length }} entries · {{ t.llmCalls }} LLM calls · {{ t.retries }} retries · total {{ (t.totalMs / 1000).toFixed(1) }} s
      · model time {{ (llmMs() / 1000).toFixed(1) }} s
    </p>
    <ol class="timeline">
      @for (s of t.steps; track $index) {
        <li [class]="s.status">
          <mat-icon class="icon">{{ icon[s.status] }}</mat-icon>
          <div class="body">
            <div class="row line">
              <b>{{ s.name }}</b>
              @if (s.attempt > 1) { <span class="chip warn">attempt {{ s.attempt }}</span> }
              <span class="chip" [class]="s.status">{{ s.status }}</span>
              @if (isLlm(s.name) && !s.note?.includes('no LLM call')) { <span class="chip">LLM</span> }
              @if (s.note?.includes('no LLM call')) { <span class="chip">Laya</span> }
              <span class="grow"></span>
              @if (s.durationMs) { <span class="muted mono">{{ (s.durationMs / 1000).toFixed(1) }} s</span> }
              @if (s.durationMs && t.totalMs) {
                <span class="bar" [style.width.px]="barWidth(s.durationMs, t.totalMs)"></span>
              }
            </div>
            @if (s.note) { <div class="note muted">{{ s.note }}</div> }
            @for (e of s.errors ?? []; track $index) {
              <div class="error">✗ {{ e }}</div>
            }
            @for (c of s.toolCalls ?? []; track $index) {
              <details class="tool">
                <summary><span class="mono">🔧 {{ c.name }}({{ c.arguments }})</span>
                  <span class="muted"> · {{ c.durationMs }} ms</span></summary>
                <pre class="mono pre">{{ c.result }}</pre>
              </details>
            }
            @if (s.output != null) {
              <details class="out">
                <summary class="muted">output</summary>
                <pre class="mono pre">{{ json(s.output) }}</pre>
              </details>
            }
          </div>
        </li>
      }
    </ol>
  `,
  styles: `
    .summary { font-size: 13px; margin: 0 0 8px; }
    .timeline { list-style: none; margin: 0; padding: 0; position: relative; }
    .timeline::before { content: ''; position: absolute; left: 11px; top: 4px; bottom: 4px; width: 2px;
                        background: var(--mat-sys-outline-variant); }
    li { display: flex; gap: 10px; padding: 6px 0; position: relative; }
    .icon { z-index: 1; background: var(--mat-sys-surface); border-radius: 50%; font-size: 24px; flex: none; }
    li.OK .icon { color: #2e7d32; }
    li.INVALID .icon, li.OVERRIDE .icon { color: #c77700; }
    li.ERROR .icon, li.FAILED .icon { color: #c62828; }
    li.INFO .icon { color: #1a5fd0; }
    li.SKIPPED .icon { color: #90a4ae; }
    .body { flex: 1; min-width: 0; }
    .line { gap: 6px; }
    .bar { height: 6px; border-radius: 3px; background: var(--mat-sys-primary); opacity: 0.5; }
    .note { font-size: 13px; }
    .error { font-size: 13px; color: #a1180f; }
    details { margin-top: 4px; font-size: 13px; }
    summary { cursor: pointer; }
    pre { margin: 4px 0 0; padding: 8px; background: var(--mat-sys-surface-container-low); border-radius: 6px;
          max-height: 320px; overflow: auto; }
  `,
})
export class TraceTimeline {
  readonly trace = input.required<PipelineTrace>();
  protected readonly icon = ICON;
  protected readonly json = json;
  protected readonly llmMs = computed(() =>
    this.trace().steps.filter((s) => LLM_STEPS.has(s.name)).reduce((a, s) => a + s.durationMs, 0),
  );

  protected isLlm(name: string): boolean {
    return LLM_STEPS.has(name);
  }

  protected barWidth(ms: number, total: number): number {
    return Math.max(2, Math.round((ms / total) * 120));
  }
}
