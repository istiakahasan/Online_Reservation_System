/**
 * Token Storage Abstraction
 * Handles storing, retrieving, and clearing JWT Access and Refresh tokens
 * across browser (localStorage/cookie) and server-side / test environments.
 */

export interface TokenStorage {
  getAccessToken(): string | null;
  getRefreshToken(): string | null;
  setTokens(tokens: { accessToken: string; refreshToken?: string }): void;
  clearTokens(): void;
}

// In-Memory Storage (Default for SSR and non-browser runtimes)
class InMemoryTokenStorage implements TokenStorage {
  private accessToken: string | null = null;
  private refreshToken: string | null = null;

  getAccessToken(): string | null {
    return this.accessToken;
  }

  getRefreshToken(): string | null {
    return this.refreshToken;
  }

  setTokens(tokens: { accessToken: string; refreshToken?: string }): void {
    this.accessToken = tokens.accessToken;
    if (tokens.refreshToken !== undefined) {
      this.refreshToken = tokens.refreshToken;
    }
  }

  clearTokens(): void {
    this.accessToken = null;
    this.refreshToken = null;
  }
}

// Browser Storage (localStorage with fallback to in-memory)
class BrowserTokenStorage implements TokenStorage {
  private memoryFallback = new InMemoryTokenStorage();
  private readonly ACCESS_TOKEN_KEY = "ors_access_token";
  private readonly REFRESH_TOKEN_KEY = "ors_refresh_token";

  private hasStorage(): boolean {
    return typeof window !== "undefined" && typeof window.localStorage !== "undefined";
  }

  getAccessToken(): string | null {
    if (!this.hasStorage()) {
      return this.memoryFallback.getAccessToken();
    }
    try {
      return window.localStorage.getItem(this.ACCESS_TOKEN_KEY);
    } catch {
      return this.memoryFallback.getAccessToken();
    }
  }

  getRefreshToken(): string | null {
    if (!this.hasStorage()) {
      return this.memoryFallback.getRefreshToken();
    }
    try {
      return window.localStorage.getItem(this.REFRESH_TOKEN_KEY);
    } catch {
      return this.memoryFallback.getRefreshToken();
    }
  }

  setTokens(tokens: { accessToken: string; refreshToken?: string }): void {
    if (!this.hasStorage()) {
      this.memoryFallback.setTokens(tokens);
      return;
    }
    try {
      window.localStorage.setItem(this.ACCESS_TOKEN_KEY, tokens.accessToken);
      if (tokens.refreshToken !== undefined) {
        window.localStorage.setItem(this.REFRESH_TOKEN_KEY, tokens.refreshToken);
      }
    } catch {
      this.memoryFallback.setTokens(tokens);
    }
  }

  clearTokens(): void {
    if (!this.hasStorage()) {
      this.memoryFallback.clearTokens();
      return;
    }
    try {
      window.localStorage.removeItem(this.ACCESS_TOKEN_KEY);
      window.localStorage.removeItem(this.REFRESH_TOKEN_KEY);
    } catch {
      this.memoryFallback.clearTokens();
    }
  }
}

// Singleton storage instance
let currentStorage: TokenStorage = new BrowserTokenStorage();

export const tokenStorage = {
  getAccessToken(): string | null {
    return currentStorage.getAccessToken();
  },
  getRefreshToken(): string | null {
    return currentStorage.getRefreshToken();
  },
  setTokens(tokens: { accessToken: string; refreshToken?: string }): void {
    currentStorage.setTokens(tokens);
  },
  clearTokens(): void {
    currentStorage.clearTokens();
  },
  setStorageImplementation(storage: TokenStorage): void {
    currentStorage = storage;
  },
};

/**
 * Decodes a JWT payload without external libraries.
 */
export function decodeJwtPayload<T = Record<string, unknown>>(token: string): T | null {
  try {
    const parts = token.split(".");
    if (parts.length !== 3) return null;
    const base64Url = parts[1];
    const base64 = base64Url.replace(/-/g, "+").replace(/_/g, "/");
    const jsonPayload =
      typeof atob !== "undefined"
        ? decodeURIComponent(
            atob(base64)
              .split("")
              .map((c) => "%" + ("00" + c.charCodeAt(0).toString(16)).slice(-2))
              .join("")
          )
        : Buffer.from(base64, "base64").toString("utf-8");
    return JSON.parse(jsonPayload) as T;
  } catch {
    return null;
  }
}

/**
 * Checks whether a given JWT is expired based on the exp claim.
 */
export function isJwtExpired(token: string, bufferSeconds = 10): boolean {
  const payload = decodeJwtPayload<{ exp?: number }>(token);
  if (!payload || !payload.exp) return true;
  const currentTime = Math.floor(Date.now() / 1000);
  return payload.exp <= currentTime + bufferSeconds;
}

