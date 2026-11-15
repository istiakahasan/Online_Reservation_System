/**
 * Session Management Utilities
 * Inspects user authentication status, decoded token claims, and session lifetime.
 */

import { tokenStorage, decodeJwtPayload, isJwtExpired } from "./tokens.ts";
import { authInterceptor } from "./interceptor.ts";
import type { AuthenticatedUser } from "../api/types.ts";

export interface SessionState {
  isAuthenticated: boolean;
  user: AuthenticatedUser | null;
  accessToken: string | null;
  isTokenExpired: boolean;
}

interface JwtTokenClaims {
  sub?: string;
  userId?: string;
  tenantId?: string;
  businessId?: string;
  email?: string;
  role?: "ADMIN" | "BUSINESS_OWNER" | "STAFF" | "CUSTOMER";
  exp?: number;
  iat?: number;
}

export function getSession(): SessionState {
  const accessToken = tokenStorage.getAccessToken();
  const refreshToken = tokenStorage.getRefreshToken();

  if (!accessToken && !refreshToken) {
    return {
      isAuthenticated: false,
      user: null,
      accessToken: null,
      isTokenExpired: true,
    };
  }

  const claims = accessToken ? decodeJwtPayload<JwtTokenClaims>(accessToken) : null;
  const expired = accessToken ? isJwtExpired(accessToken) : true;

  const user: AuthenticatedUser | null = claims
    ? {
        userId: claims.userId || claims.sub,
        tenantId: claims.tenantId || claims.businessId,
        email: claims.email,
        role: claims.role,
      }
    : null;

  return {
    isAuthenticated: !!refreshToken || (!expired && !!accessToken),
    user,
    accessToken,
    isTokenExpired: expired,
  };
}

export function isAuthenticated(): boolean {
  return getSession().isAuthenticated;
}

export async function logout(): Promise<void> {
  const refreshToken = tokenStorage.getRefreshToken();
  if (refreshToken) {
    try {
      await fetch(`${authInterceptor.getBaseUrl()}/api/auth/logout`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken }),
      });
    } catch (e) {
      console.warn("Logout network request failed, proceeding to clear client session", e);
    }
  }
  tokenStorage.clearTokens();
}
