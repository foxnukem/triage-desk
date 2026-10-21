import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'admin' },
  // The widget page loads only the widget: no Material, no admin code.
  { path: 'widget', loadComponent: () => import('./widget-page/widget-page').then((m) => m.WidgetPage), title: 'Gadgetko Support' },
  {
    path: 'admin',
    loadComponent: () => import('./admin/shell').then((m) => m.Shell),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'queue' },
      { path: 'queue', loadComponent: () => import('./admin/queue').then((m) => m.QueuePage), title: 'Queue · TriageDesk' },
      { path: 'tickets/:id', loadComponent: () => import('./admin/ticket').then((m) => m.TicketPage), title: 'Ticket · TriageDesk' },
      { path: 'playground', loadComponent: () => import('./admin/playground').then((m) => m.PlaygroundPage), title: 'Playground · TriageDesk' },
      { path: 'eval', loadComponent: () => import('./admin/eval').then((m) => m.EvalPage), title: 'Evaluation · TriageDesk' },
      { path: 'kb', loadComponent: () => import('./admin/kb').then((m) => m.KbPage), title: 'Knowledge base · TriageDesk' },
    ],
  },
  { path: '**', redirectTo: 'admin' },
];
