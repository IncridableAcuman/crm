import { describe, it, expect, vi, beforeEach } from 'vitest';
import { systemApi } from './systemApi';
import { apiClient } from '@/shared/api/client';

vi.mock('@/shared/api/client', () => ({
  apiClient: {
    get: vi.fn(),
  },
}));

describe('systemApi', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('fetches backend health metadata from /health', async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({
      data: {
        success: true,
        data: {
          status: 'UP',
          system: 'SAVDOCRM',
          version: '0.0.1-SNAPSHOT',
          serverTime: '2026-11-26T08:00:00Z',
          framework: 'Spring Boot 4.2.0 / Java 21',
        },
        timestamp: '2026-11-26T08:00:00Z',
      },
    });

    const health = await systemApi.getHealth();
    expect(apiClient.get).toHaveBeenCalledWith('/health');
    expect(health.status).toBe('UP');
    expect(health.system).toBe('SAVDOCRM');
    expect(health.version).toBe('0.0.1-SNAPSHOT');
  });
});
