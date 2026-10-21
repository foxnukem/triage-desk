import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';

import { CATEGORY_LABEL, pct, seconds } from '../core/format';
import { TriageResult } from '../core/models';

/** Category, priority, routing and entities of one triage result. */
@Component({
  selector: 'app-triage-card',
  imports: [MatIconModule, MatTooltipModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let r = result();
    @let c = r.classification;
    <section class="card" [class.flagged]="!!r.ruleOverride">
      <div class="row">
        <span class="chip" [class]="c.priority.toLowerCase()">{{ c.priority }}</span>
        <strong>{{ label[c.category] }}</strong>
        <span class="muted">→ queue <b>{{ r.queue }}</b></span>
        <span class="grow"></span>
        @if (r.needsHuman) {
          <span class="chip warn" matTooltip="Why a human must look at this">
            <mat-icon inline>person</mat-icon> {{ r.escalationReason }}</span>
        } @else {
          <span class="chip good">draft ready for review</span>
        }
      </div>

      @if (r.ruleOverride; as o) {
        <div class="override">
          <mat-icon inline>bolt</mat-icon>
          Rule <b>{{ o.ruleId }}</b> ({{ o.reason }}) matched “{{ o.matched }}”:
          @if (o.categoryChanged) { category {{ label[o.modelCategory] }} → {{ label[c.category] }}; }
          @if (o.priorityRaised) { priority {{ o.modelPriority }} → {{ c.priority }} }
          @else { priority kept }
        </div>
      }

      <p class="reason muted">{{ c.priorityReason }}</p>

      <dl>
        <dt>Sentiment</dt><dd>{{ c.sentiment.toLowerCase() }}</dd>
        <dt>Language</dt><dd>{{ c.language }}</dd>
        <dt>Confidence</dt><dd [class.low]="c.confidence < 0.6">{{ pct(c.confidence) }}</dd>
        <dt>Multi-intent</dt><dd>{{ c.multiIntent ? 'yes' : 'no' }}</dd>
        <dt>Order</dt><dd class="mono">{{ r.extraction.orderId ?? '–' }}</dd>
        <dt>Email</dt><dd>{{ r.extraction.email ?? '–' }}</dd>
        <dt>Product</dt><dd>{{ r.extraction.product ?? '–' }}</dd>
        <dt>Amount</dt><dd>{{ r.extraction.amount ?? '–' }}</dd>
      </dl>
      <p class="summary">{{ r.extraction.summary }}</p>
      <p class="muted small">
        model <span class="mono">{{ r.model }}</span> · {{ r.trace.llmCalls }} LLM calls · {{ r.trace.retries }} retries
        · {{ seconds(r.trace.totalMs) }}
        @if (r.options.classifier === 'laya') { · classifier <b>Laya</b> (encoder) }
        @if (r.options.megaPrompt) { · <b>mega-prompt baseline</b> }
        @if (!r.options.ruleRouter) { · router off }
        @if (!r.options.validation) { · validation off }
        @if (!r.options.critic) { · critic off }
      </p>
    </section>
  `,
  styles: `
    .card { border: 1px solid var(--mat-sys-outline-variant); border-radius: 10px; padding: 14px 16px; }
    .card.flagged { border-color: #e0a100; box-shadow: inset 4px 0 0 #e0a100; }
    .override { margin-top: 10px; padding: 6px 10px; background: #fff6e0; border-radius: 6px; font-size: 13px; color: #6b4300; }
    .reason { margin: 10px 0 6px; font-size: 13px; }
    dl { display: grid; grid-template-columns: max-content 1fr max-content 1fr; gap: 4px 12px; margin: 8px 0; font-size: 14px; }
    dt { color: var(--mat-sys-on-surface-variant); }
    dd { margin: 0; }
    dd.low { color: #a1180f; font-weight: 600; }
    .summary { font-style: italic; margin: 6px 0; }
    .small { font-size: 12px; margin: 6px 0 0; }
    mat-icon[inline] { vertical-align: -2px; }
    @media (max-width: 600px) { dl { grid-template-columns: max-content 1fr; } }
  `,
})
export class TriageCard {
  readonly result = input.required<TriageResult>();
  protected readonly label = CATEGORY_LABEL;
  protected readonly pct = pct;
  protected readonly seconds = seconds;
}
