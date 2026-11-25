import { describe, it, expect, vi, beforeEach } from 'vitest';
import { AxiosError, AxiosHeaders } from 'axios';
import { parseApiError } from './errorHandler';

function makeAxiosError(status: number, data: unknown, code?: string): AxiosError {
  const err = new AxiosError('Request failed', code);
  err.response = {
    status,
    statusText: 'Error',
    headers: new AxiosHeaders(),
    config: { headers: new AxiosHeaders() },
    data,
  };
  return err;
}

describe('parseApiError (RFC7807 Problem Details & Network Resilience)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('extracts field-level validation errors on 400 Bad Request', () => {
    const err = makeAxiosError(400, {
      title: 'Validation Failed',
      status: 400,
      detail: 'Validation failed for one or more fields',
      errors: {
        code: 'Role code must consist of 3 to 50 uppercase alphanumeric characters',
      },
    });

    const parsed = parseApiError(err);
    expect(parsed.status).toBe(400);
    expect(parsed.fieldErrors.code).toContain('3 to 50 uppercase');
    expect(parsed.message).toContain('code:');
  });

  it('parses 401, 403, 404, 409, and 422 ProblemDetail responses', () => {
    expect(parseApiError(makeAxiosError(401, { detail: 'Unauthorized' })).status).toBe(401);
    expect(
      parseApiError(makeAxiosError(403, { detail: 'Only an eligible active OWNER can assign OWNER.' })).message
    ).toBe('Only an eligible active OWNER can assign OWNER.');
    expect(parseApiError(makeAxiosError(404, { message: 'Branch not found' })).message).toBe(
      'Branch not found'
    );
    expect(
      parseApiError(makeAxiosError(409, { detail: 'Cannot revoke last active OWNER.' })).message
    ).toBe('Cannot revoke last active OWNER.');
    expect(
      parseApiError(makeAxiosError(422, { detail: 'Semantic validation error' })).message
    ).toBe('Semantic validation error');
  });

  it('hides internal details on 500 Internal Server Error', () => {
    const err = makeAxiosError(500, {
      detail: 'NullPointerException at com.savdocrm.SecretInternalService:42',
    });

    const parsed = parseApiError(err);
    expect(parsed.status).toBe(500);
    expect(parsed.message).not.toContain('NullPointerException');
    expect(parsed.message).toBe('An unexpected server error occurred. Please try again later.');
  });

  it('handles network failures and timeouts cleanly', () => {
    const networkErr = new AxiosError('Network Error');
    const parsedNet = parseApiError(networkErr);
    expect(parsedNet.isNetworkError).toBe(true);
    expect(parsedNet.message).toContain('Network error');

    const timeoutErr = new AxiosError('timeout of 15000ms exceeded', 'ECONNABORTED');
    const parsedTimeout = parseApiError(timeoutErr);
    expect(parsedTimeout.isTimeout).toBe(true);
    expect(parsedTimeout.message).toContain('timed out');
  });
});
