import { describe, it, expect, afterEach, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders, stubRoutes } from '../../test/helpers';
import LoginPage from './LoginPage';

describe('LoginPage', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('shows inline validation errors for invalid input', async () => {
    const restore = stubRoutes({});
    renderWithProviders(<LoginPage />, ['/login']);
    await userEvent.type(screen.getByLabelText('Email'), 'not-an-email');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByText('Enter a valid email address')).toBeInTheDocument();
    expect(screen.getByText('Password is required')).toBeInTheDocument();
    restore();
  });

  it('calls the auth API and surfaces backend error messages', async () => {
    const original = globalThis.fetch;
    globalThis.fetch = async () =>
      new Response(
        JSON.stringify({ status: 401, code: 'INVALID_CREDENTIALS', message: 'Invalid email or password.' }),
        { status: 401, headers: { 'Content-Type': 'application/json' } },
      );
    renderWithProviders(<LoginPage />, ['/login']);
    await userEvent.type(screen.getByLabelText('Email'), 'a@example.com');
    await userEvent.type(screen.getByLabelText('Password'), 'password-123');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    const errors = await screen.findAllByText('Invalid email or password.');
    expect(errors.length).toBeGreaterThanOrEqual(1);
    globalThis.fetch = original;
  });

  it('has a link to forgot-password and register', () => {
    const restore = stubRoutes({});
    renderWithProviders(<LoginPage />, ['/login']);
    expect(screen.getByRole('link', { name: 'Forgot password?' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Create an account' })).toBeInTheDocument();
    restore();
  });
});