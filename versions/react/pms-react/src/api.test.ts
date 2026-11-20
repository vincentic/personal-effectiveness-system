import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const fetchMock = vi.fn<typeof fetch>();
let storage: Map<string, string>;
let api: typeof import("./api").api;
let currentToken: typeof import("./api").currentToken;
let setToken: typeof import("./api").setToken;

beforeEach(async () => {
  storage = new Map();
  vi.stubGlobal("sessionStorage", {
    getItem: (key: string) => storage.get(key) ?? null,
    setItem: (key: string, value: string) => storage.set(key, value),
    removeItem: (key: string) => storage.delete(key),
  });
  vi.stubGlobal("fetch", fetchMock);
  vi.stubGlobal("window", new EventTarget());
  fetchMock.mockReset();
  vi.resetModules();
  ({ api, currentToken, setToken } = await import("./api"));
});

afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
});

describe("API client", () => {
  it("uses the configured API origin", async () => {
    vi.stubEnv("VITE_API_BASE_URL", "http://localhost:8080/");
    vi.resetModules();
    const configuredApi = (await import("./api")).api;
    fetchMock.mockResolvedValue(
      new Response(JSON.stringify({}), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );

    await configuredApi.me();

    expect(fetchMock.mock.calls[0]?.[0]).toBe("http://localhost:8080/api/me");
  });

  it("sends login credentials as JSON", async () => {
    fetchMock.mockResolvedValue(
      new Response(JSON.stringify({ token: "new-token", user: {} }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );

    await api.login("alice", "secret");

    const [, init] = fetchMock.mock.calls[0]!;
    expect(new Headers(init?.headers).get("Content-Type")).toBe(
      "application/json",
    );
    expect(init?.body).toBe(
      JSON.stringify({ username: "alice", password: "secret" }),
    );
  });

  it("sends the stored bearer token with authenticated requests", async () => {
    setToken("active-token");
    fetchMock.mockResolvedValue(
      new Response(JSON.stringify({}), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );

    await api.me();

    const [, init] = fetchMock.mock.calls[0]!;
    expect(new Headers(init?.headers).get("Authorization")).toBe(
      "Bearer active-token",
    );
  });

  it("clears the stored token after an unauthorized response", async () => {
    setToken("expired-token");
    fetchMock.mockResolvedValue(
      new Response(JSON.stringify({ detail: "认证已过期" }), {
        status: 401,
        headers: { "Content-Type": "application/json" },
      }),
    );

    await expect(api.me()).rejects.toThrow("认证已过期");
    expect(currentToken()).toBe("");
    expect(storage.has("pms.react.token")).toBe(false);
  });

  it("accepts an empty 204 response when deleting a record", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

    await expect(api.deleteDaily(42)).resolves.toBeUndefined();
  });
});
