import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { StatusChip } from '../StatusChip';

describe('StatusChip', () => {
  it('always carries a word, never colour alone', () => {
    render(<StatusChip status="PROVISIONAL" />);
    expect(screen.getByText('PROVISIONAL')).toHaveClass('chip', 'c-prov');
  });
  it('adds a pulsing dot for live', () => {
    const { container } = render(<StatusChip status="LIVE" />);
    expect(container.querySelector('.dot')).not.toBeNull();
  });
});
