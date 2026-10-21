import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';

import { Api } from '../core/api';
import { KbStore } from '../core/kb-store';
import { KbHit } from '../core/models';

/** Read-only KB, plus the same vector search the reply agent's searchKb tool uses. */
@Component({
  selector: 'app-kb',
  imports: [FormsModule, MatExpansionModule, MatInputModule, MatButtonModule, MatIconModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <h1>Knowledge base <span class="muted count">{{ kb.articles().length }} articles</span></h1>

      <form class="row search" (ngSubmit)="search()">
        <mat-form-field class="grow" subscriptSizing="dynamic">
          <mat-label>Test retrieval (what searchKb would return)</mat-label>
          <input matInput name="q" [(ngModel)]="q" placeholder="посилка не прийшла / refund for damaged phone" />
        </mat-form-field>
        <button mat-flat-button type="submit" [disabled]="!q().trim()"><mat-icon>search</mat-icon> Search</button>
      </form>
      @if (hits(); as hits) {
        <div class="hits">
          @for (h of hits; track h.articleId) {
            <div class="hit"><span class="mono score">{{ h.score.toFixed(3) }}</span> <b>{{ h.title }}</b>
              <span class="mono muted">{{ h.articleId }}</span></div>
          } @empty {
            <p class="muted">No article above the similarity threshold.</p>
          }
        </div>
      }

      <input class="filter" placeholder="Filter by title or id" [value]="filter()" (input)="filter.set($any($event.target).value)" />
      <mat-accordion multi>
        @for (a of visible(); track a.id) {
          <mat-expansion-panel>
            <mat-expansion-panel-header>
              <mat-panel-title>{{ a.title }}</mat-panel-title>
              <mat-panel-description class="mono">{{ a.id }}</mat-panel-description>
            </mat-expansion-panel-header>
            <div class="pre body">{{ a.body }}</div>
          </mat-expansion-panel>
        }
      </mat-accordion>
    </div>
  `,
  styles: `
    .count { font: var(--mat-sys-body-medium); }
    .search { margin-bottom: 8px; max-width: 760px; }
    .hits { margin-bottom: 16px; max-width: 760px; }
    .hit { padding: 4px 0; border-bottom: 1px solid var(--mat-sys-outline-variant); }
    .score { display: inline-block; width: 48px; color: var(--mat-sys-primary); }
    .filter { font: inherit; padding: 8px 10px; width: 280px; margin-bottom: 12px; border-radius: 6px;
              border: 1px solid var(--mat-sys-outline-variant); background: transparent; color: inherit; }
    .body { font-size: 14px; }
  `,
})
export class KbPage implements OnInit {
  protected readonly kb = inject(KbStore);
  private readonly api = inject(Api);
  protected readonly filter = signal('');
  protected readonly q = signal('');
  protected readonly hits = signal<KbHit[] | null>(null);
  protected readonly visible = computed(() => {
    const f = this.filter().toLowerCase();
    return this.kb.articles().filter((a) => !f || a.title.toLowerCase().includes(f) || a.id.includes(f));
  });

  ngOnInit(): void {
    this.kb.load();
  }

  protected search(): void {
    this.api.kbSearch(this.q().trim()).subscribe((h) => this.hits.set(h));
  }
}
