#!/usr/bin/env python3
"""
Chaos Test Suite for Bank Statement Microservices
=================================================
Simulates real-world scenarios to validate the architecture:
- Flood test: massive concurrent transactions
- Concurrent transfers: simultaneous operations on same accounts
- Multi-currency storm: cross-currency exchanges at the same time
- Service kill: stops a container mid-saga to test compensation
- Reconciliation check: verifies balance consistency across services

Usage:
  python chaos_test.py [test_name] [--transactions N] [--workers N]

Examples:
  python chaos_test.py flood --transactions 500
  python chaos_test.py concurrent_transfers --workers 20
  python chaos_test.py multi_currency_storm
  python chaos_test.py service_kill --target ledger-service
  python chaos_test.py reconciliation
  python chaos_test.py all
"""

import asyncio
import argparse
import json
import random
import sys
import time
from datetime import datetime

import httpx
from rich.console import Console
from rich.table import Table
from rich.progress import Progress, SpinnerColumn, BarColumn, TextColumn
from rich.panel import Panel
from rich.live import Live

console = Console()

ORCHESTRATOR_URL = "http://localhost:8080"
ACCOUNT_URL = "http://localhost:8081"
LEDGER_URL = "http://localhost:8082"
TRANSACTION_URL = "http://localhost:8083"
STATEMENT_URL = "http://localhost:8085"

ACCOUNTS = ["account-joao-001", "account-maria-002"]
CURRENCIES = ["BRL", "USD", "EUR"]
TRANSACTION_TYPES = ["DEPOSIT", "WITHDRAWAL", "TRANSFER_OUT"]


class TestResult:
    def __init__(self, name):
        self.name = name
        self.total = 0
        self.success = 0
        self.failed = 0
        self.errors = []
        self.latencies = []
        self.start_time = None
        self.end_time = None

    def start(self):
        self.start_time = time.time()

    def stop(self):
        self.end_time = time.time()

    @property
    def duration(self):
        if self.start_time and self.end_time:
            return self.end_time - self.start_time
        return 0

    @property
    def avg_latency(self):
        return sum(self.latencies) / len(self.latencies) if self.latencies else 0

    @property
    def p95_latency(self):
        if not self.latencies:
            return 0
        sorted_l = sorted(self.latencies)
        idx = int(len(sorted_l) * 0.95)
        return sorted_l[idx]

    @property
    def throughput(self):
        return self.total / self.duration if self.duration > 0 else 0


async def send_transaction(client, source, target, tx_type, amount, currency,
                           target_currency=None, description=None):
    """Send a transaction through the saga orchestrator."""
    body = {
        "sourceAccountId": source,
        "transactionType": tx_type,
        "amount": amount,
        "currency": currency,
        "description": description or f"Chaos test - {tx_type}",
    }
    if target:
        body["targetAccountId"] = target
    if target_currency:
        body["targetCurrency"] = target_currency

    start = time.time()
    try:
        resp = await client.post(f"{ORCHESTRATOR_URL}/api/saga/transaction", json=body)
        latency = time.time() - start
        if resp.status_code == 200:
            return True, latency, resp.json()
        return False, latency, {"error": resp.text}
    except Exception as e:
        return False, time.time() - start, {"error": str(e)}


async def poll_saga(client, saga_id, timeout=30):
    """Poll saga status until completed or timeout."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            resp = await client.get(f"{ORCHESTRATOR_URL}/api/saga/status/{saga_id}")
            if resp.status_code == 200:
                data = resp.json()
                status = data.get("currentStatus", "")
                if status in ("COMPLETED", "FAILED", "COMPENSATION_COMPLETED"):
                    return data
        except Exception:
            pass
        await asyncio.sleep(0.5)
    return {"currentStatus": "TIMEOUT"}


# ─── Test: Flood ─────────────────────────────────────────

async def test_flood(num_transactions=100, workers=10):
    """Send N transactions as fast as possible to test throughput."""
    result = TestResult("Flood Test")
    console.print(Panel(
        f"[bold]Flood Test[/bold]\n"
        f"Sending {num_transactions} transactions with {workers} concurrent workers",
        style="cyan"
    ))

    semaphore = asyncio.Semaphore(workers)

    async def worker(client, i):
        async with semaphore:
            source = random.choice(ACCOUNTS)
            target = [a for a in ACCOUNTS if a != source][0]
            tx_type = random.choice(["DEPOSIT", "TRANSFER_OUT", "WITHDRAWAL"])
            amount = round(random.uniform(1, 100), 2)
            currency = random.choice(["BRL", "USD", "EUR"])

            target_acc = target if tx_type == "TRANSFER_OUT" else None
            ok, latency, data = await send_transaction(
                client, source, target_acc, tx_type, amount, currency,
                description=f"Flood #{i}"
            )

            result.total += 1
            result.latencies.append(latency)
            if ok:
                result.success += 1
            else:
                result.failed += 1
                result.errors.append(data.get("error", "Unknown"))

    result.start()
    async with httpx.AsyncClient(timeout=30) as client:
        tasks = [worker(client, i) for i in range(num_transactions)]
        with Progress(
            SpinnerColumn(), BarColumn(), TextColumn("[progress.percentage]{task.percentage:>3.0f}%"),
            console=console
        ) as progress:
            task = progress.add_task("Sending...", total=num_transactions)
            for coro in asyncio.as_completed(tasks):
                await coro
                progress.advance(task)

    result.stop()
    print_result(result)
    return result


# ─── Test: Concurrent Transfers ──────────────────────────

async def test_concurrent_transfers(workers=20):
    """Multiple transfers between the same two accounts simultaneously."""
    result = TestResult("Concurrent Transfers")
    console.print(Panel(
        f"[bold]Concurrent Transfers[/bold]\n"
        f"{workers} simultaneous transfers between João ↔ Maria",
        style="yellow"
    ))

    result.start()
    async with httpx.AsyncClient(timeout=30) as client:
        tasks = []
        for i in range(workers):
            direction = i % 2 == 0
            source = ACCOUNTS[0] if direction else ACCOUNTS[1]
            target = ACCOUNTS[1] if direction else ACCOUNTS[0]
            amount = round(random.uniform(10, 200), 2)

            tasks.append(send_transaction(
                client, source, target, "TRANSFER_OUT", amount, "BRL",
                description=f"Concurrent #{i}"
            ))

        results_raw = await asyncio.gather(*tasks)
        for ok, latency, data in results_raw:
            result.total += 1
            result.latencies.append(latency)
            if ok:
                result.success += 1
            else:
                result.failed += 1

    result.stop()

    console.print("\n[bold]Waiting for sagas to complete...[/bold]")
    await asyncio.sleep(5)

    print_result(result)
    return result


# ─── Test: Multi-Currency Storm ──────────────────────────

async def test_multi_currency_storm(num_exchanges=30):
    """Cross-currency exchanges happening simultaneously."""
    result = TestResult("Multi-Currency Storm")
    pairs = [
        ("BRL", "USD"), ("USD", "EUR"), ("EUR", "BRL"),
        ("BRL", "EUR"), ("USD", "BRL"), ("EUR", "USD"),
    ]

    console.print(Panel(
        f"[bold]Multi-Currency Storm[/bold]\n"
        f"{num_exchanges} simultaneous currency exchanges across {len(pairs)} pairs",
        style="magenta"
    ))

    result.start()
    async with httpx.AsyncClient(timeout=30) as client:
        tasks = []
        for i in range(num_exchanges):
            source_cur, target_cur = random.choice(pairs)
            amount = round(random.uniform(50, 500), 2)
            account = random.choice(ACCOUNTS)

            tasks.append(send_transaction(
                client, account, None, "CURRENCY_EXCHANGE", amount, source_cur,
                target_currency=target_cur,
                description=f"Exchange #{i} {source_cur}->{target_cur}"
            ))

        results_raw = await asyncio.gather(*tasks)
        for ok, latency, data in results_raw:
            result.total += 1
            result.latencies.append(latency)
            if ok:
                result.success += 1
            else:
                result.failed += 1

    result.stop()
    console.print("\n[bold]Waiting for exchange sagas...[/bold]")
    await asyncio.sleep(8)

    print_result(result)
    return result


# ─── Test: Service Kill ──────────────────────────────────

async def test_service_kill(target_service="ledger-service"):
    """Stop a service mid-saga to test compensation."""
    result = TestResult(f"Service Kill ({target_service})")

    console.print(Panel(
        f"[bold]Service Kill Test[/bold]\n"
        f"Will stop '{target_service}' during transaction processing\n"
        f"Then verify saga compensation works correctly",
        style="red"
    ))

    try:
        import docker
        docker_client = docker.from_env()
    except Exception as e:
        console.print(f"[red]Docker not available: {e}[/red]")
        console.print("[yellow]Skipping service kill test (requires Docker access)[/yellow]")
        result.total = 1
        result.failed = 1
        result.errors.append("Docker not available")
        return result

    result.start()
    async with httpx.AsyncClient(timeout=30) as client:
        console.print("[yellow]1. Sending transaction...[/yellow]")
        ok, latency, data = await send_transaction(
            client, ACCOUNTS[0], ACCOUNTS[1], "TRANSFER_OUT", 100.0, "BRL",
            description="Service kill test"
        )
        result.total += 1
        result.latencies.append(latency)

        if ok:
            saga_id = data.get("sagaId")
            console.print(f"[yellow]2. Saga started: {saga_id}[/yellow]")

            await asyncio.sleep(0.5)

            console.print(f"[red]3. Stopping {target_service}...[/red]")
            try:
                container = docker_client.containers.get(target_service)
                container.stop(timeout=2)
                console.print(f"[red]   {target_service} stopped![/red]")

                await asyncio.sleep(3)

                console.print(f"[green]4. Restarting {target_service}...[/green]")
                container.start()
                await asyncio.sleep(5)

                console.print("[yellow]5. Checking saga status...[/yellow]")
                saga_result = await poll_saga(client, saga_id, timeout=30)
                status = saga_result.get("currentStatus", "UNKNOWN")

                console.print(f"   Saga final status: [bold]{status}[/bold]")
                if status in ("COMPLETED", "COMPENSATING", "COMPENSATION_COMPLETED"):
                    result.success += 1
                else:
                    result.failed += 1

            except Exception as e:
                console.print(f"[red]Error during service kill: {e}[/red]")
                result.failed += 1
                result.errors.append(str(e))
        else:
            result.failed += 1

    result.stop()
    print_result(result)
    return result


# ─── Test: Reconciliation ────────────────────────────────

async def test_reconciliation():
    """Check balance consistency across all services."""
    result = TestResult("Reconciliation Check")

    console.print(Panel(
        "[bold]Reconciliation Check[/bold]\n"
        "Comparing balances across account-service, ledger-service, and statement-service",
        style="green"
    ))

    result.start()
    async with httpx.AsyncClient(timeout=15) as client:
        for account_id in ACCOUNTS:
            console.print(f"\n[bold]Account: {account_id}[/bold]")
            result.total += 1

            try:
                acc_resp = await client.get(f"{ACCOUNT_URL}/api/accounts/{account_id}")
                acc_data = acc_resp.json() if acc_resp.status_code == 200 else {}
                acc_balances = {b["currency"]: b["balance"] for b in acc_data.get("balances", [])}

                ledger_resp = await client.get(f"{LEDGER_URL}/api/ledger/entries/account/{account_id}")
                ledger_entries = ledger_resp.json() if ledger_resp.status_code == 200 else []

                stmt_resp = await client.get(f"{STATEMENT_URL}/api/statements/{account_id}")
                stmt_data = stmt_resp.json() if stmt_resp.status_code == 200 else {}
                stmt_balances = {b["currency"]: b["balance"] for b in stmt_data.get("currentBalances", [])}

                table = Table(title=f"Balance Comparison - {account_id[:20]}...")
                table.add_column("Currency", style="cyan")
                table.add_column("Account Service", style="green")
                table.add_column("Statement Service", style="yellow")
                table.add_column("Ledger Entries", style="magenta")
                table.add_column("Match", style="bold")

                all_currencies = set(list(acc_balances.keys()) + list(stmt_balances.keys()))
                all_match = True

                for currency in sorted(all_currencies):
                    acc_bal = acc_balances.get(currency, "N/A")
                    stmt_bal = stmt_balances.get(currency, "N/A")
                    ledger_count = sum(1 for e in ledger_entries
                                       if (e.get("debitAccountId") == account_id or
                                           e.get("creditAccountId") == account_id))

                    match = "N/A"
                    if acc_bal != "N/A" and stmt_bal != "N/A":
                        match = "✓" if abs(float(acc_bal) - float(stmt_bal)) < 0.01 else "✗"
                        if match == "✗":
                            all_match = False

                    table.add_row(
                        currency,
                        str(acc_bal),
                        str(stmt_bal),
                        str(ledger_count),
                        match
                    )

                console.print(table)

                if all_match:
                    result.success += 1
                else:
                    result.failed += 1
                    result.errors.append(f"Balance mismatch for {account_id}")

            except Exception as e:
                result.failed += 1
                result.errors.append(f"Error checking {account_id}: {e}")

    result.stop()
    print_result(result)
    return result


def print_result(result):
    """Print formatted test results."""
    table = Table(title=f"Results: {result.name}", show_lines=True)
    table.add_column("Metric", style="bold")
    table.add_column("Value", style="cyan")

    table.add_row("Total Requests", str(result.total))
    table.add_row("Successful", f"[green]{result.success}[/green]")
    table.add_row("Failed", f"[red]{result.failed}[/red]")
    table.add_row("Duration", f"{result.duration:.2f}s")
    table.add_row("Throughput", f"{result.throughput:.1f} req/s")
    table.add_row("Avg Latency", f"{result.avg_latency*1000:.0f}ms")
    table.add_row("P95 Latency", f"{result.p95_latency*1000:.0f}ms")
    table.add_row("Success Rate", f"{(result.success/result.total*100) if result.total > 0 else 0:.1f}%")

    if result.errors:
        table.add_row("Errors", "\n".join(result.errors[:5]))

    console.print(table)


async def run_all(args):
    """Run all tests sequentially."""
    console.print(Panel(
        "[bold]CHAOS TEST SUITE[/bold]\n"
        "Bank Statement Microservices - Orchestrated Saga\n"
        f"Started at {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
        style="bold blue"
    ))

    results = []

    results.append(await test_flood(args.transactions, args.workers))
    await asyncio.sleep(3)

    results.append(await test_concurrent_transfers(args.workers))
    await asyncio.sleep(3)

    results.append(await test_multi_currency_storm())
    await asyncio.sleep(3)

    results.append(await test_reconciliation())

    console.print("\n")
    summary = Table(title="CHAOS TEST SUMMARY", show_lines=True)
    summary.add_column("Test", style="bold")
    summary.add_column("Total")
    summary.add_column("Success", style="green")
    summary.add_column("Failed", style="red")
    summary.add_column("Throughput")
    summary.add_column("Avg Latency")

    for r in results:
        summary.add_row(
            r.name, str(r.total), str(r.success), str(r.failed),
            f"{r.throughput:.1f}/s", f"{r.avg_latency*1000:.0f}ms"
        )

    console.print(summary)


def main():
    parser = argparse.ArgumentParser(description="Chaos Test Suite for Bank Statement Microservices")
    parser.add_argument("test", nargs="?", default="all",
                        choices=["all", "flood", "concurrent_transfers",
                                 "multi_currency_storm", "service_kill", "reconciliation"],
                        help="Test to run")
    parser.add_argument("--transactions", "-n", type=int, default=100,
                        help="Number of transactions for flood test")
    parser.add_argument("--workers", "-w", type=int, default=10,
                        help="Number of concurrent workers")
    parser.add_argument("--target", "-t", type=str, default="ledger-service",
                        help="Target service for service_kill test")

    args = parser.parse_args()

    test_map = {
        "flood": lambda: test_flood(args.transactions, args.workers),
        "concurrent_transfers": lambda: test_concurrent_transfers(args.workers),
        "multi_currency_storm": lambda: test_multi_currency_storm(),
        "service_kill": lambda: test_service_kill(args.target),
        "reconciliation": lambda: test_reconciliation(),
        "all": lambda: run_all(args),
    }

    asyncio.run(test_map[args.test]())


if __name__ == "__main__":
    main()
