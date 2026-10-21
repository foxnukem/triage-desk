import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';

import { KbArticle } from '../core/models';

@Component({
  selector: 'app-kb-article-dialog',
  imports: [MatDialogModule, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>{{ a.title }}</h2>
    <mat-dialog-content>
      <p class="mono muted">{{ a.id }}</p>
      <div class="pre">{{ a.body }}</div>
    </mat-dialog-content>
    <mat-dialog-actions align="end"><button mat-button mat-dialog-close>Close</button></mat-dialog-actions>
  `,
})
export class KbArticleDialog {
  protected readonly a = inject<KbArticle>(MAT_DIALOG_DATA);
}
