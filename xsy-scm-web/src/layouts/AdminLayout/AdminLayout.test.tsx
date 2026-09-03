import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AdminLayout } from './index';

describe('AdminLayout', () => {
  it('renders the two-level product navigation', () => {
    render(
      <MemoryRouter>
        <AdminLayout />
      </MemoryRouter>,
    );

    expect(screen.getByRole('navigation', { name: '一级导航' })).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: '商品二级导航' })).toBeInTheDocument();
    expect(screen.getByText('商品档案')).toBeInTheDocument();
  });
});
