import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  DecisionAction,
  EvalConfig,
  KbArticle,
  KbHit,
  Page,
  PipelineOptions,
  RunDetail,
  RunSummary,
  Sample,
  Stats,
  TicketDetail,
  TicketRow,
  TriageResult,
} from './models';

export interface QueueFilter {
  status?: string;
  category?: string;
  priority?: string;
  needsHuman?: boolean | null;
  sort?: string;
  page?: number;
  size?: number;
}

/** Admin API. The widget does not use this: it talks to /api/public on its own. */
@Injectable({ providedIn: 'root' })
export class Api {
  private readonly http = inject(HttpClient);

  tickets(f: QueueFilter): Observable<Page<TicketRow>> {
    let params = new HttpParams();
    for (const [k, v] of Object.entries(f)) {
      if (v !== undefined && v !== null && v !== '') {
        params = params.set(k, String(v));
      }
    }
    return this.http.get<Page<TicketRow>>('/api/tickets', { params });
  }

  ticket(id: number): Observable<TicketDetail> {
    return this.http.get<TicketDetail>(`/api/tickets/${id}`);
  }

  createTicket(text: string, email?: string): Observable<{ id: number; status: string }> {
    return this.http.post<{ id: number; status: string }>('/api/tickets', { text, email: email || null });
  }

  retriage(id: number, model?: string, classifier?: string): Observable<unknown> {
    return this.http.post(`/api/tickets/${id}/retriage`, { model: model || null, classifier: classifier || null });
  }

  decide(id: number, action: DecisionAction, finalText?: string, note?: string): Observable<TicketDetail> {
    return this.http.post<TicketDetail>(`/api/tickets/${id}/decision`, { action, finalText, note });
  }

  stats(): Observable<Stats> {
    return this.http.get<Stats>('/api/stats');
  }

  triage(text: string, opts: Partial<PipelineOptions>): Observable<TriageResult> {
    return this.http.post<TriageResult>('/api/triage', { text, ...opts });
  }

  samples(): Observable<Sample[]> {
    return this.http.get<Sample[]>('/api/triage/samples');
  }

  defaults(): Observable<{ defaults: PipelineOptions; classifiers: string[] }> {
    return this.http.get<{ defaults: PipelineOptions; classifiers: string[] }>('/api/triage/options');
  }

  models(): Observable<string[]> {
    return this.http.get<string[]>('/api/models');
  }

  kb(): Observable<KbArticle[]> {
    return this.http.get<KbArticle[]>('/api/kb');
  }

  kbSearch(q: string): Observable<KbHit[]> {
    return this.http.get<KbHit[]>('/api/kb/search', { params: { q } });
  }

  evalPresets(): Observable<string[]> {
    return this.http.get<string[]>('/api/eval/presets');
  }

  evalRuns(): Observable<RunSummary[]> {
    return this.http.get<RunSummary[]>('/api/eval/runs');
  }

  evalRun(id: number): Observable<RunDetail> {
    return this.http.get<RunDetail>(`/api/eval/runs/${id}`);
  }

  startEval(cfg: EvalConfig): Observable<{ runId: number }> {
    return this.http.post<{ runId: number }>('/api/eval/run', cfg);
  }
}
