import { formatDate, registerLocaleData } from '@angular/common';
import localePt from '@angular/common/locales/pt';

import { DATE, DATE_TIME, DATE_TIME_SECONDS, formatMonth } from './date-formats';

describe('date formats shown to the user', () => {
  beforeAll(() => registerLocaleData(localePt));

  it('formats dates as dd/MM/yyyy, with optional HH:mm and HH:mm:ss', () => {
    const instant = new Date(2026, 0, 5, 9, 7, 3);

    expect(formatDate(instant, DATE, 'pt-BR')).toBe('05/01/2026');
    expect(formatDate(instant, DATE_TIME, 'pt-BR')).toBe('05/01/2026 09:07');
    expect(formatDate(instant, DATE_TIME_SECONDS, 'pt-BR')).toBe('05/01/2026 09:07:03');
  });

  it('formats the competence month of the API as MM/yyyy', () => {
    expect(formatMonth('2026-11')).toBe('11/2026');
    expect(formatMonth(null)).toBe('—');
  });
});
