import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { SupportWidgetComponent, TicketCreatedEvent, WidgetLocale, WidgetTheme } from 'widget';

/** Stand-in for a customer-facing site that embeds the widget. */
@Component({
  selector: 'app-widget-page',
  imports: [SupportWidgetComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main>
      <header>
        <strong>Gadgetko</strong>
        <span class="switch">
          <button type="button" (click)="locale.set(locale() === 'uk' ? 'en' : 'uk')">{{ locale() === 'uk' ? 'EN' : 'UA' }}</button>
          <button type="button" (click)="theme.set(theme() === 'light' ? 'dark' : 'light')">{{ theme() === 'light' ? '☾' : '☀' }}</button>
        </span>
      </header>
      <gk-support-widget [locale]="locale()" [theme]="theme()" (ticketCreated)="lastCreated.set($event)" />
      @if (lastCreated(); as t) {
        <p class="event">ticketCreated → id {{ t.id }}, status {{ t.status }}</p>
      }
    </main>
  `,
  styles: `
    :host { display: block; min-height: 100vh; background: #f4f5f7; }
    main { max-width: 480px; margin: 0 auto; padding: 24px 16px; display: grid; gap: 16px; }
    header { display: flex; justify-content: space-between; align-items: center; font-size: 20px; }
    .switch { display: flex; gap: 6px; }
    .switch button { border: 1px solid #ccc; background: #fff; border-radius: 6px; padding: 4px 10px; cursor: pointer; }
    .event { font: 12px ui-monospace, monospace; color: #666; margin: 0; }
  `,
})
export class WidgetPage {
  protected readonly locale = signal<WidgetLocale>('uk');
  protected readonly theme = signal<WidgetTheme>('light');
  protected readonly lastCreated = signal<TicketCreatedEvent | null>(null);
}
