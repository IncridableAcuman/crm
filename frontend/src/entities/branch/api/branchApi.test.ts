import { describe, it, expect, vi, beforeEach } from 'vitest';
import { branchApi } from './branchApi';
import { apiClient } from '@/shared/api/client';
import { Branch, CreateBranchRequest } from '../model/types';

vi.mock('@/shared/api/client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

describe('branchApi', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  const mockBranch: Branch = {
    id: 'branch-1',
    tenantId: 'tenant-1',
    code: 'BR-01',
    name: 'Chorsu Store',
    phone: '+998901234567',
    address: 'Chorsu Bazaar',
    isActive: true,
    isMain: true,
    createdAt: '2026-11-24T12:00:00Z',
    updatedAt: '2026-11-24T12:00:00Z',
  };

  it('fetches all active branches for current tenant', async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({
      data: {
        success: true,
        data: [mockBranch],
        timestamp: '2026-11-24T12:00:00Z',
      },
    });

    const branches = await branchApi.getBranches();
    expect(apiClient.get).toHaveBeenCalledWith('/branches');
    expect(branches).toHaveLength(1);
    expect(branches[0]?.code).toBe('BR-01');
    expect(branches[0]?.isMain).toBe(true);
  });

  it('fetches a single branch by id', async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({
      data: {
        success: true,
        data: mockBranch,
        timestamp: '2026-11-24T12:00:00Z',
      },
    });

    const branch = await branchApi.getBranchById('branch-1');
    expect(apiClient.get).toHaveBeenCalledWith('/branches/branch-1');
    expect(branch.name).toBe('Chorsu Store');
  });

  it('creates a new branch matching backend CreateBranchCommand', async () => {
    const payload: CreateBranchRequest = {
      code: 'BR-02',
      name: 'Yunusobod Branch',
      isMain: false,
    };

    vi.mocked(apiClient.post).mockResolvedValueOnce({
      data: {
        success: true,
        data: { ...mockBranch, id: 'branch-2', code: 'BR-02', name: 'Yunusobod Branch', isMain: false },
        timestamp: '2026-11-24T12:00:00Z',
      },
    });

    const created = await branchApi.createBranch(payload);
    expect(apiClient.post).toHaveBeenCalledWith('/branches', payload);
    expect(created.id).toBe('branch-2');
    expect(created.code).toBe('BR-02');
    expect(created.isMain).toBe(false);
  });
});
