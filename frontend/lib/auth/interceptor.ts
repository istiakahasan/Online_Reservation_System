/**
 * Auth Fetch Interceptor with Automatic Refresh on 401
 *
 * Implements a thread-safe mutex and subscriber queue:
 * - Automatically injects Bearer accessToken to outgoing requests.
 * - Detects HTTP 401 Unauthorized responses.
 * - Queues concurrent requests while a single refresh request runs.
 * - Rotates refresh tokens via POST /api/auth/refresh.
 * - Replays queued requests transparently with the new token.
 * - Clears session and rejects queue if refresh fails, avoiding infinite loops.
 */

import { tokenStorage } from "./tokens.ts";

export interface CustomRequestInit extends RequestInit {
  _retry?: boolean;
  skipAuth?: boolean;
}

export type AuthExpiredCallback = () => void;

class AuthInterceptorManager {
  private isRefreshing = false;
  private refreshSubscribers: Array<{
    resolve: (token: string) => void;
    reject: (error: Error) => void;
  }> = [];
  private authExpiredCallbacks: Set<AuthExpiredCallback> = new Set();
  private baseUrl: string =
    typeof process !== "undefined" && process.env?.NEXT_PUBLIC_API_URL
      ? process.env.NEXT_PUBLIC_API_URL
      : "http://localhost:8080";

  public setBaseUrl(url: string): void {
    this.baseUrl = url.replace(/\/+$/, "");
  }

  public getBaseUrl(): string {
    return this.baseUrl;
  }

  public onAuthExpired(callback: AuthExpiredCallback): () => void {
    this.authExpiredCallbacks.add(callback);
    return () => {
      this.authExpiredCallbacks.delete(callback);
    };
  }

  private notifyAuthExpired(): void {
    this.authExpiredCallbacks.forEach((cb) => {
      try {
        cb();
      } catch (err) {
        console.error("Error in onAuthExpired callback", err);
      }
    });
  }

  private subscribeTokenRefresh(): Promise<string> {
    return new Promise((resolve, reject) => {
      this.refreshSubscribers.push({ resolve, reject });
    });
  }

  private onRefreshed(newAccessToken: string): void {
    this.refreshSubscribers.forEach(({ resolve }) => resolve(newAccessToken));
    this.refreshSubscribers = [];
  }

  private onRefreshFailed(error: Error): void {
    this.refreshSubscribers.forEach(({ reject }) => reject(error));
    this.refreshSubscribers = [];
  }

  /**
   * Refreshes the token pair by calling the backend /api/auth/refresh endpoint.
   */
  public async executeRefreshToken(): Promise<string> {
    const currentRefreshToken = tokenStorage.getRefreshToken();
    if (!currentRefreshToken) {
      tokenStorage.clearTokens();
      this.notifyAuthExpired();
      throw new Error("No refresh token available");
    }

    const refreshUrl = `${this.baseUrl}/api/auth/refresh`;

    try {
      const response = await fetch(refreshUrl, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Accept: "application/json",
        },
        body: JSON.stringify({ refreshToken: currentRefreshToken }),
      });

      if (!response.ok) {
        throw new Error(`Token refresh failed with status ${response.status}`);
      }

      const data = await response.json();
      const newAccessToken = data.accessToken;
      const newRefreshToken = data.refreshToken;

      if (!newAccessToken) {
        throw new Error("Invalid refresh response payload: missing accessToken");
      }

      // Save rotated tokens
      tokenStorage.setTokens({
        accessToken: newAccessToken,
        refreshToken: newRefreshToken || currentRefreshToken,
      });

      return newAccessToken;
    } catch (error) {
      tokenStorage.clearTokens();
      this.notifyAuthExpired();
      throw error instanceof Error ? error : new Error(String(error));
    }
  }

  /**
   * Main intercepted fetch function.
   */
  public async fetchWithAuth(
    input: RequestInfo | URL,
    init: CustomRequestInit = {}
  ): Promise<Response> {
    let urlString = typeof input === "string" ? input : input.toString();

    // Prepend baseUrl if relative path
    if (urlString.startsWith("/")) {
      urlString = `${this.baseUrl}${urlString}`;
    }

    const headers = new Headers(init.headers || {});

    // Attach Bearer token if not skipped and available
    if (!init.skipAuth && !headers.has("Authorization")) {
      const token = tokenStorage.getAccessToken();
      if (token) {
        headers.set("Authorization", `Bearer ${token}`);
      }
    }

    // Default JSON accept header
    if (!headers.has("Accept")) {
      headers.set("Accept", "application/json");
    }

    const requestInit: CustomRequestInit = {
      ...init,
      headers,
    };

    // Execute original request
    let response = await fetch(urlString, requestInit);

    // If 401 Unauthorized and not already retried and not the refresh/login endpoint
    const isAuthEndpoint =
      urlString.includes("/api/auth/refresh") ||
      urlString.includes("/api/auth/login") ||
      urlString.includes("/api/auth/register");

    if (response.status === 401 && !requestInit._retry && !isAuthEndpoint) {
      requestInit._retry = true;

      if (!this.isRefreshing) {
        this.isRefreshing = true;

        try {
          const newAccessToken = await this.executeRefreshToken();
          this.onRefreshed(newAccessToken);

          // Retry the current request with the new token
          headers.set("Authorization", `Bearer ${newAccessToken}`);
          response = await fetch(urlString, {
            ...requestInit,
            headers,
          });
        } catch (refreshErr) {
          this.onRefreshFailed(
            refreshErr instanceof Error ? refreshErr : new Error(String(refreshErr))
          );
          return response; // Return the 401 response
        } finally {
          this.isRefreshing = false;
        }
      } else {
        // Wait for active refresh to finish
        try {
          const newAccessToken = await this.subscribeTokenRefresh();
          headers.set("Authorization", `Bearer ${newAccessToken}`);
          response = await fetch(urlString, {
            ...requestInit,
            headers,
          });
        } catch {
          return response; // Return original 401 if refresh failed
        }
      }
    }

    return response;
  }
}

// Global Singleton
export const authInterceptor = new AuthInterceptorManager();

/**
 * Convenient wrapper for fetch with auth interceptor
 */
export const authFetch = (
  input: RequestInfo | URL,
  init?: CustomRequestInit
): Promise<Response> => authInterceptor.fetchWithAuth(input, init);
