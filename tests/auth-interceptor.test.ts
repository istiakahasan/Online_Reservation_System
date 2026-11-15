import test, { describe, beforeEach } from "node:test";
import assert from "node:assert";
import { tokenStorage, decodeJwtPayload, isJwtExpired } from "../lib/auth/tokens.ts";
import { authInterceptor, authFetch } from "../lib/auth/interceptor.ts";
import { apiClient, ApiClientError } from "../lib/api/client.ts";
import { authApi } from "../lib/api/endpoints/auth.ts";
import { availabilityApi } from "../lib/api/endpoints/availability.ts";
import { reservationsApi } from "../lib/api/endpoints/reservations.ts";

describe("Token Storage and JWT Utilities", () => {
  beforeEach(() => {
    tokenStorage.clearTokens();
  });

  test("should store and retrieve access and refresh tokens", () => {
    tokenStorage.setTokens({
      accessToken: "mock-access-token-123",
      refreshToken: "mock-refresh-token-456",
    });

    assert.strictEqual(tokenStorage.getAccessToken(), "mock-access-token-123");
    assert.strictEqual(tokenStorage.getRefreshToken(), "mock-refresh-token-456");
  });

  test("should clear tokens properly", () => {
    tokenStorage.setTokens({
      accessToken: "token-to-clear",
      refreshToken: "refresh-to-clear",
    });
    tokenStorage.clearTokens();

    assert.strictEqual(tokenStorage.getAccessToken(), null);
    assert.strictEqual(tokenStorage.getRefreshToken(), null);
  });

  test("should decode valid JWT payload and verify expiration", () => {
    const pastTime = Math.floor(Date.now() / 1000) - 3600; // 1 hour ago
    const futureTime = Math.floor(Date.now() / 1000) + 3600; // 1 hour ahead

    const header = Buffer.from(JSON.stringify({ alg: "HS256", typ: "JWT" })).toString("base64url");
    const expiredPayload = Buffer.from(
      JSON.stringify({ sub: "user-1", email: "user@example.com", exp: pastTime })
    ).toString("base64url");
    const activePayload = Buffer.from(
      JSON.stringify({ sub: "user-2", email: "active@example.com", exp: futureTime })
    ).toString("base64url");

    const expiredJwt = `${header}.${expiredPayload}.mockSignature`;
    const activeJwt = `${header}.${activePayload}.mockSignature`;

    assert.strictEqual(isJwtExpired(expiredJwt), true);
    assert.strictEqual(isJwtExpired(activeJwt), false);

    const decoded = decodeJwtPayload<{ sub: string; email: string }>(activeJwt);
    assert.strictEqual(decoded?.sub, "user-2");
    assert.strictEqual(decoded?.email, "active@example.com");
  });
});

describe("Auth Fetch Interceptor", () => {
  const originalFetch = globalThis.fetch;

  beforeEach(() => {
    tokenStorage.clearTokens();
    authInterceptor.setBaseUrl("http://localhost:8080");
  });

  test("should automatically attach Authorization header when accessToken exists", async () => {
    tokenStorage.setTokens({
      accessToken: "valid-bearer-token",
      refreshToken: "valid-refresh-token",
    });

    let capturedHeaders: Headers | null = null;

    globalThis.fetch = async (input: RequestInfo | URL, init?: RequestInit) => {
      capturedHeaders = new Headers(init?.headers);
      return new Response(JSON.stringify({ success: true }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    };

    try {
      const response = await authFetch("/api/test-protected");
      assert.strictEqual(response.status, 200);
      assert.strictEqual(capturedHeaders?.get("Authorization"), "Bearer valid-bearer-token");
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  test("should not attach Authorization header if skipAuth is set", async () => {
    tokenStorage.setTokens({
      accessToken: "valid-bearer-token",
      refreshToken: "valid-refresh-token",
    });

    let capturedHeaders: Headers | null = null;

    globalThis.fetch = async (input: RequestInfo | URL, init?: RequestInit) => {
      capturedHeaders = new Headers(init?.headers);
      return new Response(JSON.stringify({ public: true }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    };

    try {
      const response = await authFetch("/api/public-endpoint", { skipAuth: true });
      assert.strictEqual(response.status, 200);
      assert.strictEqual(capturedHeaders?.has("Authorization"), false);
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  test("should catch 401, refresh tokens via POST /api/auth/refresh, rotate tokens, and replay original request", async () => {
    tokenStorage.setTokens({
      accessToken: "expired-access-token",
      refreshToken: "valid-old-refresh-token",
    });

    let callCount = 0;
    let refreshCallCount = 0;
    let finalReplayToken: string | null = null;

    globalThis.fetch = async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);

      if (url.endsWith("/api/auth/refresh")) {
        refreshCallCount++;
        const body = JSON.parse(String(init?.body));
        assert.strictEqual(body.refreshToken, "valid-old-refresh-token");

        return new Response(
          JSON.stringify({
            accessToken: "new-rotated-access-token",
            refreshToken: "new-rotated-refresh-token",
            tokenType: "Bearer",
            expiresIn: 900,
            userId: "u-123",
            businessId: "b-123",
            role: "CUSTOMER",
          }),
          { status: 200, headers: { "Content-Type": "application/json" } }
        );
      }

      if (url.endsWith("/api/reservations/res-1")) {
        callCount++;
        const auth = new Headers(init?.headers).get("Authorization");

        if (callCount === 1) {
          assert.strictEqual(auth, "Bearer expired-access-token");
          // Return 401 on first call
          return new Response(
            JSON.stringify({ status: 401, errorCode: "UNAUTHORIZED", message: "Token expired" }),
            { status: 401, headers: { "Content-Type": "application/json" } }
          );
        } else {
          finalReplayToken = auth;
          return new Response(
            JSON.stringify({ id: "res-1", status: "CONFIRMED" }),
            { status: 200, headers: { "Content-Type": "application/json" } }
          );
        }
      }

      throw new Error(`Unexpected URL: ${url}`);
    };

    try {
      const res = await authFetch("/api/reservations/res-1");
      const data = await res.json();

      assert.strictEqual(res.status, 200);
      assert.strictEqual(data.id, "res-1");
      assert.strictEqual(refreshCallCount, 1);
      assert.strictEqual(callCount, 2);
      assert.strictEqual(finalReplayToken, "Bearer new-rotated-access-token");
      assert.strictEqual(tokenStorage.getAccessToken(), "new-rotated-access-token");
      assert.strictEqual(tokenStorage.getRefreshToken(), "new-rotated-refresh-token");
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  test("should queue multiple concurrent 401s so that only ONE refresh call is dispatched", async () => {
    tokenStorage.setTokens({
      accessToken: "expired-access-token",
      refreshToken: "valid-old-refresh-token",
    });

    let refreshCallCount = 0;
    let requestCount = 0;

    globalThis.fetch = async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);

      if (url.endsWith("/api/auth/refresh")) {
        refreshCallCount++;
        // Simulate minor async network delay
        await new Promise((r) => setTimeout(r, 20));
        return new Response(
          JSON.stringify({
            accessToken: "single-rotated-token",
            refreshToken: "new-refresh-token",
            tokenType: "Bearer",
            expiresIn: 900,
          }),
          { status: 200, headers: { "Content-Type": "application/json" } }
        );
      }

      if (url.includes("/api/item-")) {
        requestCount++;
        const auth = new Headers(init?.headers).get("Authorization");

        if (auth === "Bearer expired-access-token") {
          return new Response(
            JSON.stringify({ status: 401, errorCode: "UNAUTHORIZED" }),
            { status: 401, headers: { "Content-Type": "application/json" } }
          );
        }

        return new Response(
          JSON.stringify({ item: url.split("/api/")[1], receivedAuth: auth }),
          { status: 200, headers: { "Content-Type": "application/json" } }
        );
      }

      throw new Error(`Unexpected URL: ${url}`);
    };

    try {
      // Fire 5 concurrent requests simultaneously
      const results = await Promise.all([
        authFetch("/api/item-1").then((r) => r.json()),
        authFetch("/api/item-2").then((r) => r.json()),
        authFetch("/api/item-3").then((r) => r.json()),
        authFetch("/api/item-4").then((r) => r.json()),
        authFetch("/api/item-5").then((r) => r.json()),
      ]);

      // Exactly ONE refresh request should have fired
      assert.strictEqual(refreshCallCount, 1);
      // All 5 requests should have replayed and succeeded with the new rotated token
      assert.strictEqual(results.length, 5);
      results.forEach((r, idx) => {
        assert.strictEqual(r.item, `item-${idx + 1}`);
        assert.strictEqual(r.receivedAuth, "Bearer single-rotated-token");
      });
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  test("should clear storage, notify onAuthExpired, and avoid loop when refresh fails", async () => {
    tokenStorage.setTokens({
      accessToken: "expired-access-token",
      refreshToken: "revoked-refresh-token",
    });

    let expiredNotified = false;
    const unsubscribe = authInterceptor.onAuthExpired(() => {
      expiredNotified = true;
    });

    globalThis.fetch = async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith("/api/auth/refresh")) {
        return new Response(
          JSON.stringify({ status: 401, errorCode: "REFRESH_TOKEN_EXPIRED", message: "Token revoked" }),
          { status: 401, headers: { "Content-Type": "application/json" } }
        );
      }

      return new Response(
        JSON.stringify({ status: 401, message: "Unauthorized" }),
        { status: 401, headers: { "Content-Type": "application/json" } }
      );
    };

    try {
      const response = await authFetch("/api/user/profile");
      assert.strictEqual(response.status, 401);
      assert.strictEqual(expiredNotified, true);
      assert.strictEqual(tokenStorage.getAccessToken(), null);
      assert.strictEqual(tokenStorage.getRefreshToken(), null);
    } finally {
      unsubscribe();
      globalThis.fetch = originalFetch;
    }
  });
});

describe("Typed API Client and Endpoints", () => {
  const originalFetch = globalThis.fetch;

  beforeEach(() => {
    tokenStorage.clearTokens();
  });

  test("apiClient should serialize query parameters and parse responses", async () => {
    let capturedUrl = "";

    globalThis.fetch = async (input: RequestInfo | URL) => {
      capturedUrl = String(input);
      return new Response(
        JSON.stringify([{ staffId: "st-1", available: true, durationMinutes: 60 }]),
        { status: 200, headers: { "Content-Type": "application/json" } }
      );
    };

    try {
      const slots = await availabilityApi.getAvailability({
        businessId: "b-100",
        serviceId: "s-200",
        staffId: "st-300",
        date: "2026-11-20",
      });

      assert.strictEqual(slots.length, 1);
      assert.strictEqual(slots[0].available, true);
      assert.ok(capturedUrl.includes("/api/businesses/b-100/availability"));
      assert.ok(capturedUrl.includes("serviceId=s-200"));
      assert.ok(capturedUrl.includes("staffId=st-300"));
      assert.ok(capturedUrl.includes("date=2026-11-20"));
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  test("apiClient should throw typed ApiClientError on HTTP 409 conflict", async () => {
    globalThis.fetch = async () => {
      return new Response(
        JSON.stringify({
          status: 409,
          errorCode: "SLOT_ALREADY_BOOKED",
          message: "The requested time slot has already been reserved",
        }),
        { status: 409, headers: { "Content-Type": "application/json" } }
      );
    };

    try {
      await reservationsApi.createReservation({
        businessId: "b-1",
        customerId: "c-1",
        serviceId: "s-1",
        staffId: "st-1",
        startTime: "2026-11-20T10:00:00Z",
      });
      assert.fail("Should have thrown ApiClientError");
    } catch (err) {
      assert.ok(err instanceof ApiClientError);
      assert.strictEqual(err.status, 409);
      assert.strictEqual(err.errorCode, "SLOT_ALREADY_BOOKED");
      assert.ok(err.message.includes("already been reserved"));
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  test("authApi.login should save tokens and return user AuthResponse", async () => {
    globalThis.fetch = async () => {
      return new Response(
        JSON.stringify({
          accessToken: "jwt-access-abc",
          refreshToken: "jwt-refresh-xyz",
          tokenType: "Bearer",
          expiresIn: 900,
          userId: "u-99",
          businessId: "b-88",
          role: "CUSTOMER",
        }),
        { status: 200, headers: { "Content-Type": "application/json" } }
      );
    };

    try {
      const auth = await authApi.login({
        businessId: "b-88",
        email: "client@example.com",
        password: "SecretPassword123!",
      });

      assert.strictEqual(auth.accessToken, "jwt-access-abc");
      assert.strictEqual(auth.role, "CUSTOMER");
      assert.strictEqual(tokenStorage.getAccessToken(), "jwt-access-abc");
      assert.strictEqual(tokenStorage.getRefreshToken(), "jwt-refresh-xyz");
    } finally {
      globalThis.fetch = originalFetch;
    }
  });
});

