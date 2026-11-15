/**
 * Core Typed API Client
 * Wraps authFetch with typed requests, query string serialization, and ErrorResponse handling.
 */

import { authFetch } from "../auth/interceptor.ts";
import type { CustomRequestInit } from "../auth/interceptor.ts";
import type { ErrorResponse } from "./types.ts";

export interface RequestOptions extends Omit<CustomRequestInit, "body"> {
  params?: Record<string, string | number | boolean | undefined | null>;
  body?: unknown;
}

export class ApiClientError extends Error {
  public status: number;
  public errorCode?: string;
  public details?: Record<string, string> | null;
  public rawResponse?: unknown;

  constructor(status: number, message: string, errorCode?: string, details?: Record<string, string> | null, rawResponse?: unknown) {
    super(message);
    this.name = "ApiClientError";
    this.status = status;
    this.errorCode = errorCode;
    this.details = details;
    this.rawResponse = rawResponse;
  }
}

export class ApiClient {
  public async request<T>(endpoint: string, options: RequestOptions = {}): Promise<T> {
    const { params, body, headers, ...restInit } = options;

    let url = endpoint;
    if (params) {
      const searchParams = new URLSearchParams();
      Object.entries(params).forEach(([key, value]) => {
        if (value !== undefined && value !== null) {
          searchParams.append(key, String(value));
        }
      });
      const queryString = searchParams.toString();
      if (queryString) {
        url += (url.includes("?") ? "&" : "?") + queryString;
      }
    }

    const requestHeaders = new Headers(headers || {});
    let serializedBody: BodyInit | undefined;

    if (body !== undefined && body !== null) {
      if (body instanceof FormData || typeof body === "string") {
        serializedBody = body as BodyInit;
      } else {
        serializedBody = JSON.stringify(body);
        if (!requestHeaders.has("Content-Type")) {
          requestHeaders.set("Content-Type", "application/json");
        }
      }
    }

    const response = await authFetch(url, {
      ...restInit,
      headers: requestHeaders,
      body: serializedBody,
    });

    if (!response.ok) {
      let errorData: ErrorResponse | null = null;
      try {
        errorData = await response.json();
      } catch {
        // Response was not JSON
      }

      throw new ApiClientError(
        response.status,
        errorData?.message || `HTTP Request failed with status ${response.status}`,
        errorData?.errorCode,
        errorData?.details,
        errorData
      );
    }

    if (response.status === 204) {
      return undefined as unknown as T;
    }

    return (await response.json()) as T;
  }

  public get<T>(url: string, options?: Omit<RequestOptions, "method" | "body">): Promise<T> {
    return this.request<T>(url, { ...options, method: "GET" });
  }

  public post<T>(url: string, body?: unknown, options?: Omit<RequestOptions, "method" | "body">): Promise<T> {
    return this.request<T>(url, { ...options, method: "POST", body });
  }

  public put<T>(url: string, body?: unknown, options?: Omit<RequestOptions, "method" | "body">): Promise<T> {
    return this.request<T>(url, { ...options, method: "PUT", body });
  }

  public delete<T>(url: string, options?: Omit<RequestOptions, "method">): Promise<T> {
    return this.request<T>(url, { ...options, method: "DELETE" });
  }
}

export const apiClient = new ApiClient();
