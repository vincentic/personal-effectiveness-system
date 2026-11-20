import type {
  DailyInput,
  DailyRecord,
  Dashboard,
  MonthlyInput,
  MonthlyRecord,
  User,
  WeeklyInput,
  WeeklyRecord,
} from "./types";

const TOKEN_KEY = "pms.fastapi.token";
const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? "").replace(/\/+$/, "");

let token = sessionStorage.getItem(TOKEN_KEY) ?? "";

export function currentToken(): string {
  return token;
}

export function setToken(value: string): void {
  token = value;
  if (value) {
    sessionStorage.setItem(TOKEN_KEY, value);
  } else {
    sessionStorage.removeItem(TOKEN_KEY);
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  if (init.body) headers.set("Content-Type", "application/json");
  if (token) headers.set("Authorization", `Bearer ${token}`);

  const response = await fetch(`${API_BASE_URL}${path}`, { ...init, headers });
  if (!response.ok) {
    let message = `请求失败（${response.status}）`;
    try {
      const body = (await response.json()) as { detail?: string };
      message = body.detail || message;
    } catch {
      // Keep the HTTP status message when the response is not JSON.
    }
    if (response.status === 401) setToken("");
    throw new Error(message);
  }
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

export const api = {
  login: (username: string, password: string) =>
    request<{ token: string; user: User }>("/api/auth/login", {
      method: "POST",
      body: JSON.stringify({ username, password }),
    }),
  me: () => request<User>("/api/me"),
  dashboard: () => request<Dashboard>("/api/dashboard"),
  daily: () => request<DailyRecord[]>("/api/daily"),
  saveDaily: (data: DailyInput, id?: number) =>
    request<DailyRecord>(id ? `/api/daily/${id}` : "/api/daily", {
      method: id ? "PUT" : "POST",
      body: JSON.stringify(data),
    }),
  deleteDaily: (id: number) =>
    request<void>(`/api/daily/${id}`, { method: "DELETE" }),
  weekly: () => request<WeeklyRecord[]>("/api/weekly"),
  saveWeekly: (data: WeeklyInput, id?: number) =>
    request<WeeklyRecord>(id ? `/api/weekly/${id}` : "/api/weekly", {
      method: id ? "PUT" : "POST",
      body: JSON.stringify(data),
    }),
  deleteWeekly: (id: number) =>
    request<void>(`/api/weekly/${id}`, { method: "DELETE" }),
  monthly: () => request<MonthlyRecord[]>("/api/monthly"),
  saveMonthly: (data: MonthlyInput, id?: number) =>
    request<MonthlyRecord>(id ? `/api/monthly/${id}` : "/api/monthly", {
      method: id ? "PUT" : "POST",
      body: JSON.stringify(data),
    }),
  deleteMonthly: (id: number) =>
    request<void>(`/api/monthly/${id}`, { method: "DELETE" }),
};
