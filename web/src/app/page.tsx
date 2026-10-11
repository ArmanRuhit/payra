import { ApiError, getHealth, type Health } from "@/lib/api";
import { Suspense } from "react";

type HealthState = 
  | { ok: true, health: Health }
  | { ok: false, reason: string };

export default async function Home() {

  return (
    <main className="flex min-h-svh items-center justify-center bg-background p-6">
      <section className="w-full max-w-md space-y-6">
        <header className="space-y-1">
          <h1 className="text-3xl font-semibold tracking-tight text-foreground">Payra</h1>
          <p className="text-sm text-muted-foreground">
            Digital wallet &amp; payments platform - foundation online
          </p>
        </header>

        <Suspense fallback={<HealthCardSkeleton />}>
          <HealthCard />
        </Suspense>

        <p className="text-xs text-muted-foreground">
          Phase 0 · foundation -- identity &amp; ledger arrive in Phase 1/2
        </p>
      </section>
    </main>
  )
}

async function HealthCard() {
  const state = await loadHealth();

  return (
    <div className="rounded-xl border border-border bg-card p-5 shadow-sm">
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-medium text-card-foreground">API status</h2>
        <StatusDot state={state} />
      </div>

      {state.ok ? (
            <ul className="mt-4 space-y-2 text-sm">
              <ComponentRow name="PostgreSQL" status={state.health.components?.db?.status} />
              <ComponentRow name="Redis" status={state.health.components?.redis?.status} />
            </ul>
          ) : (
            <p className="mt-4 text-sm text-muted-foreground">
              {state.reason}. Start the API with{" "}
              <code className="rounded bg-muted px-1.5 py-0.5 text-xs">./mvnw spring-boot:run</code>{" "} and refresh.
            </p>
      )}
    </div>
  )
}

function HealthCardSkeleton() {
  return (
    <div className="rounded-xl border border-border bg-card p-5 shadow-sm">
      <div className="flex items-center justify-between">
          <h2 className="text-sm font-medium text-card-foreground">API status</h2>
          <span className="size-2.5 animate-pulse rounded-full bg-muted" aria-hidden />
      </div>
      <ul className="mt-4 space-y-2 text-sm">
        {["PostgreSQL", "Redis"].map((name) => (
          <li key={name} className="flex items-center justify-between">
            <span className="text-muted-foreground">{name}</span>
            <span className="h-4 w-12 animate-pulse rounded bg-muted" />
          </li>
        ))}
      </ul>
    </div>
  )
}

async function loadHealth(): Promise<HealthState> {
  try {
    return { ok: true, health: await getHealth() };
  } catch (error) {
    const reason = error instanceof ApiError ? `API answered ${error.status}` : "API unreachable";

    return { ok: false, reason }
  }
}

function StatusDot({ state }: { state: HealthState }) {
  const color = !state.ok
    ? "bg-destructive"
    : state.health.status === "UP"
      ? "bg-success"
      : "bg-warning";

  return <span className={`size-2.5 rounded-full ${color}`} aria-hidden />
}

function ComponentRow({ name, status }: { name: string; status?: string}) {
  const up = status === "UP";

  return (
    <li className="flex items-center justify-between">
      <span className="text-muted-foreground">{name}</span>
      <span className={up ? "font-medium text-success" : "font-medium text-destructive"}>
        { status ?? "unknown" }
      </span>
    </li>
  );
}