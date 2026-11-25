import axios, { AxiosError } from 'axios';

export interface ProblemDetailPayload {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  timestamp?: string;
  errors?: Record<string, string>;
  message?: string;
}

export interface ParsedApiError {
  message: string;
  status?: number;
  title?: string;
  fieldErrors: Record<string, string>;
  isNetworkError: boolean;
  isTimeout: boolean;
}

const STATUS_FALLBACK_MESSAGES: Record<number, string> = {
  400: 'Invalid request parameters. Please check your input.',
  401: 'Your session has expired. Please sign in again.',
  403: 'You do not have permission to perform this action.',
  404: 'The requested resource could not be found.',
  409: 'This operation conflicts with existing data or system rules.',
  422: 'The request could not be processed due to business rule validation.',
  500: 'An unexpected server error occurred. Please try again later.',
};

/**
 * Normalizes Axios errors, RFC7807 ProblemDetail payloads, ApiResponse errors,
 * timeouts, and network failures into safe, user-friendly messages without exposing internals.
 */
export function parseApiError(error: unknown, fallbackMessage = 'Request failed. Please try again.'): ParsedApiError {
  if (axios.isAxiosError(error)) {
    const axiosErr = error as AxiosError<ProblemDetailPayload>;

    if (axiosErr.code === 'ECONNABORTED') {
      return {
        message: 'The request timed out. Please check your connection and try again.',
        fieldErrors: {},
        isNetworkError: false,
        isTimeout: true,
      };
    }

    if (!axiosErr.response) {
      return {
        message: 'Network error. Unable to reach the server.',
        fieldErrors: {},
        isNetworkError: true,
        isTimeout: false,
      };
    }

    const { status, data } = axiosErr.response;
    const fieldErrors =
      data && typeof data === 'object' && data.errors && typeof data.errors === 'object'
        ? data.errors
        : {};

    // For 5xx errors, never surface internal exception messages or stack traces
    if (status >= 500) {
      return {
        message: STATUS_FALLBACK_MESSAGES[500] ?? fallbackMessage,
        status,
        title: 'Server Error',
        fieldErrors: {},
        isNetworkError: false,
        isTimeout: false,
      };
    }

    const detail = data && typeof data === 'object' ? data.detail : undefined;
    const apiMessage = data && typeof data === 'object' ? data.message : undefined;
    const title = data && typeof data === 'object' ? data.title : undefined;

    const firstFieldError = Object.values(fieldErrors)[0];
    const resolvedMessage =
      (Object.keys(fieldErrors).length > 0 && firstFieldError
        ? `${detail || 'Validation failed'}: ${Object.entries(fieldErrors)
            .map(([field, msg]) => `${field}: ${msg}`)
            .join('; ')}`
        : detail || apiMessage) ||
      STATUS_FALLBACK_MESSAGES[status] ||
      fallbackMessage;

    return {
      message: resolvedMessage,
      status,
      title,
      fieldErrors,
      isNetworkError: false,
      isTimeout: false,
    };
  }

  if (error instanceof Error && error.message) {
    return {
      message: error.message,
      fieldErrors: {},
      isNetworkError: false,
      isTimeout: false,
    };
  }

  return {
    message: fallbackMessage,
    fieldErrors: {},
    isNetworkError: false,
    isTimeout: false,
  };
}
