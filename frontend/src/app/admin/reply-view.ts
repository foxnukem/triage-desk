import { ChangeDetectionStrategy, Component, OnInit, inject, input } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';

import { KbStore } from '../core/kb-store';
import { ReplyDraft } from '../core/models';
import { KbArticleDialog } from './kb-article-dialog';

/** The draft reply with its cited KB articles (click to read the source). */
@Component({
  selector: 'app-reply-view',
  imports: [MatIconModule, MatTooltipModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let d = draft();
    <div class="text pre">{{ d.text }}</div>
    <div class="row cites">
      <span class="muted">Sources:</span>
      @for (id of d.citedArticles; track id) {
        <button type="button" class="cite" (click)="open(id)" [matTooltip]="kb.byId().get(id)?.title ?? id">
          <mat-icon inline>menu_book</mat-icon> {{ id }}
        </button>
      } @empty {
        <span class="chip warn">no citations</span>
      }
      @if (d.promisesAction) {
        <span class="chip warn" matTooltip="The draft commits Gadgetko to an action">promises action</span>
      }
    </div>
    @if (lint().length) {
      <div class="lint">Policy lint: {{ lint().join('; ') }}</div>
    }
  `,
  styles: `
    .text { padding: 12px 14px; background: var(--mat-sys-surface-container-low); border-radius: 8px; }
    .cites { margin-top: 8px; font-size: 13px; }
    .cite { border: 1px solid var(--mat-sys-outline-variant); background: transparent; border-radius: 999px; padding: 2px 10px;
            cursor: pointer; color: var(--mat-sys-primary); font: 12px ui-monospace, monospace; }
    .lint { margin-top: 8px; font-size: 13px; color: #a1180f; }
    mat-icon[inline] { vertical-align: -2px; }
  `,
})
export class ReplyView implements OnInit {
  readonly draft = input.required<ReplyDraft>();
  readonly lint = input<string[]>([]);
  protected readonly kb = inject(KbStore);
  private readonly dialog = inject(MatDialog);

  ngOnInit(): void {
    this.kb.load();
  }

  protected open(id: string): void {
    const a = this.kb.byId().get(id);
    if (a) {
      this.dialog.open(KbArticleDialog, { data: a, width: '640px' });
    }
  }
}
