import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { App } from '../App';
import { ApiClient } from '../services/apiClient';

describe('API Sentinel Frontend Production Suite', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.stubGlobal('fetch', vi.fn().mockImplementation(() =>
      Promise.resolve({
        ok: true,
        status: 200,
        json: () => Promise.resolve({ data: {} }),
      } as unknown as Response)
    ));
  });

  it('renders application navigation shell and header', () => {
    render(<App />);
    expect(screen.getByText('API SENTINEL')).toBeInTheDocument();
    expect(screen.getByText('Overview Dashboard')).toBeInTheDocument();
    expect(screen.getByText('Projects Registry')).toBeInTheDocument();
    expect(screen.getByText('Endpoints & Health History')).toBeInTheDocument();
  });

  it('handles token storage and clearing correctly in ApiClient', () => {
    ApiClient.setTokens('test_access_token', 'test_refresh_token');
    expect(ApiClient.getAccessToken()).toBe('test_access_token');
    expect(ApiClient.getRefreshToken()).toBe('test_refresh_token');
    expect(localStorage.getItem('sentinel_access_token')).toBe('test_access_token');

    ApiClient.setTokens(null, null);
    expect(ApiClient.getAccessToken()).toBeNull();
    expect(ApiClient.getRefreshToken()).toBeNull();
    expect(localStorage.getItem('sentinel_access_token')).toBeNull();
  });
});
