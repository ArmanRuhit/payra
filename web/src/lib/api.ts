// Typed client for the Payra API.
// Server-side only for now: the browser talks to Next.js, Next.js talks to the API.
// When the browser needs the API directly (Phase 1 auth), it goes through a route
// handler -- no NEXT_PUBLIC_* keys leaking into the bundle.

const API_BASE_URL = process.env.API_BASE_URL ?? "http://localhost:8080";

/** RFC 7807 ProblemDetail -- the API's error shape. */
export type ProblemDetail = {
    type: string;
    title: string;
    status: number;
    detail?: string;
    code?: string;
    errors?: { field: string; message: string }[]; 
}


export class ApiError extends Error {
    readonly status: number;
    readonly problem: ProblemDetail;

    constructor(status: number, problem: ProblemDetail) {
        super(problem.detail ?? problem.title);
        this.name = "ApiError";
        this.status = status;
        this.problem = problem;
    }
}

export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
    const headers = new Headers(init?.headers);
    headers.set("Accept", "application/json");

    const response = await fetch(`${API_BASE_URL}${path}`, {
        ...init,
        headers,
        cache: "no-store", // dev-stable; per-endpoint caching decisions come later
    });

    if (!response.ok) {
        throw new ApiError(response.status, await readProblem(response));
    }

    return (await response.json()) as T;
}

async function readProblem(response: Response): Promise<ProblemDetail> {
    try {
        return (await response.json()) as ProblemDetail;
    } catch {
        return {
            type: "about:blank",
            title: response.statusText || "Request failed",
            status: response.status,
        }
    }
}

/** Actuator health payload (trimmed to what we render). */
export type Health = {
    status: "UP" | "DOWN" | "OUT_OF_SERVICE" | "UNKNOWN";
    components?: Record<string, { status: string }>;
};

export function getHealth(): Promise<Health> {
    return apiFetch<Health>("/actuator/health");
}