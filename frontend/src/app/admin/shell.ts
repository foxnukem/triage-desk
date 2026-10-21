import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatSidenavModule, MatToolbarModule, MatListModule, MatIconModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <mat-toolbar class="bar">
      <mat-icon>support_agent</mat-icon>
      <span class="brand">TriageDesk</span>
      <span class="muted sub">Gadgetko support · agent panel</span>
      <span class="grow"></span>
      <a class="widget-link" href="/widget" target="_blank" rel="noopener">Customer widget ↗</a>
    </mat-toolbar>
    <mat-sidenav-container class="container">
      <mat-sidenav mode="side" opened class="nav">
        <mat-nav-list>
          @for (l of links; track l.path) {
            <a mat-list-item [routerLink]="l.path" routerLinkActive #rla="routerLinkActive" [activated]="rla.isActive">
              <mat-icon matListItemIcon>{{ l.icon }}</mat-icon>
              <span matListItemTitle>{{ l.label }}</span>
            </a>
          }
        </mat-nav-list>
      </mat-sidenav>
      <mat-sidenav-content>
        <router-outlet />
      </mat-sidenav-content>
    </mat-sidenav-container>
  `,
  styles: `
    :host { display: flex; flex-direction: column; height: 100vh; }
    .bar { gap: 10px; background: var(--mat-sys-surface-container); border-bottom: 1px solid var(--mat-sys-outline-variant); }
    .brand { font-weight: 700; }
    .sub { font-size: 14px; }
    .grow { flex: 1; }
    .widget-link { font-size: 14px; color: var(--mat-sys-primary); text-decoration: none; }
    .container { flex: 1; }
    .nav { width: 210px; border-right: 1px solid var(--mat-sys-outline-variant); }
    @media (max-width: 800px) { .sub { display: none; } .nav { width: 64px; } .nav [matListItemTitle] { display: none; } }
  `,
})
export class Shell {
  protected readonly links = [
    { path: 'queue', label: 'Queue', icon: 'inbox' },
    { path: 'playground', label: 'Playground', icon: 'science' },
    { path: 'eval', label: 'Evaluation', icon: 'insights' },
    { path: 'kb', label: 'Knowledge base', icon: 'menu_book' },
  ];
}
