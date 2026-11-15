import { apiClient } from "../client.ts";
import { tokenStorage } from "../../auth/tokens.ts";
import type {
  AuthResponse,
  LoginRequest,
  RegisterRequest,
  RefreshTokenRequest,
  LogoutRequest,
  AuthenticatedUser,
} from "../types.ts";

export const authApi = {
  /**
   * Registers a customer under a tenant and saves initial token pair.
   */
  async register(request: RegisterRequest): Promise<AuthResponse> {
    const response = await apiClient.post<AuthResponse>("/api/auth/register", request, {
      skipAuth: true,
    });
    tokenStorage.setTokens({
      accessToken: response.accessToken,
      refreshToken: response.refreshToken,
    });
    return response;
  },

  /**
   * Logs in a user and saves the returned accessToken + refreshToken.
   */
  async login(request: LoginRequest): Promise<AuthResponse> {
    const response = await apiClient.post<AuthResponse>("/api/auth/login", request, {
      skipAuth: true,
    });
    tokenStorage.setTokens({
      accessToken: response.accessToken,
      refreshToken: response.refreshToken,
    });
    return response;
  },

  /**
   * Rotates refresh token manually.
   */
  async refresh(request: RefreshTokenRequest): Promise<AuthResponse> {
    const response = await apiClient.post<AuthResponse>("/api/auth/refresh", request, {
      skipAuth: true,
    });
    tokenStorage.setTokens({
      accessToken: response.accessToken,
      refreshToken: response.refreshToken,
    });
    return response;
  },

  /**
   * Revokes refresh token and clears client storage.
   */
  async logout(request?: LogoutRequest): Promise<void> {
    const refreshToken = request?.refreshToken || tokenStorage.getRefreshToken();
    if (refreshToken) {
      await apiClient.post<void>("/api/auth/logout", { refreshToken }, { skipAuth: true });
    }
    tokenStorage.clearTokens();
  },

  /**
   * Gets current user principal details from Bearer token.
   */
  async getCurrentUser(): Promise<AuthenticatedUser> {
    return apiClient.get<AuthenticatedUser>("/api/auth/me");
  },
};
