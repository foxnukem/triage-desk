import { Injectable, computed, inject, signal } from '@angular/core';

import { Api } from './api';
import { KbArticle } from './models';

/** The KB is small and read-only: load it once, share it. */
@Injectable({ providedIn: 'root' })
export class KbStore {
  private readonly api = inject(Api);
  private readonly loaded = signal<KbArticle[] | null>(null);
  private requested = false;

  readonly articles = computed(() => this.loaded() ?? []);
  readonly byId = computed(() => new Map(this.articles().map((a) => [a.id, a])));

  load(): void {
    if (!this.requested) {
      this.requested = true;
      this.api.kb().subscribe({ next: (a) => this.loaded.set(a), error: () => (this.requested = false) });
    }
  }
}
