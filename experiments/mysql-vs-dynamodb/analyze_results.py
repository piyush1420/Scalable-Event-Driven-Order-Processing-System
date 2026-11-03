import json

with open("combined_results.json") as f:
    data = json.load(f)

def calc_stats(results):
    times = sorted([r["response_time"] for r in results])
    successes = sum(1 for r in results if r["success"])
    n = len(times)
    return {
        "count": n,
        "success": successes,
        "avg": round(sum(times) / n, 2),
        "p50": round(times[n // 2], 2),
        "p95": round(times[int(n * 0.95)], 2),
        "p99": round(times[int(n * 0.99)], 2),
        "min": round(min(times), 2),
        "max": round(max(times), 2),
    }

mysql = [r for r in data if r["database"] == "mysql"]
dynamo = [r for r in data if r["database"] == "dynamodb"]

print("=" * 60)
print("STEP III: COMPLETE ANALYSIS FROM combined_results.json")
print("=" * 60)

# Part 0: Verification
print(f"\n--- Part 0: Data Verification ---")
print(f"MySQL operations:   {len(mysql)}")
print(f"DynamoDB operations: {len(dynamo)}")
print(f"Combined total:     {len(data)}")

# Overall stats
m_all = calc_stats(mysql)
d_all = calc_stats(dynamo)

print(f"\n--- Part 1: Overall Comparison Table ---")
print(f"{'Metric':<30} {'MySQL':>10} {'DynamoDB':>10} {'Winner':>10} {'Margin':>10}")
print("-" * 70)

metrics = [
    ("Avg Response Time (ms)", m_all["avg"], d_all["avg"]),
    ("P50 Response Time (ms)", m_all["p50"], d_all["p50"]),
    ("P95 Response Time (ms)", m_all["p95"], d_all["p95"]),
    ("P99 Response Time (ms)", m_all["p99"], d_all["p99"]),
    ("Success Rate (%)", round(m_all["success"]/m_all["count"]*100, 1), round(d_all["success"]/d_all["count"]*100, 1)),
]

for name, m_val, d_val in metrics:
    if m_val <= d_val:
        winner = "MySQL"
        margin = round(d_val - m_val, 2)
    else:
        winner = "DynamoDB"
        margin = round(m_val - d_val, 2)
    if name == "Success Rate (%)" and m_val == d_val:
        winner = "Tie"
        margin = "0%"
    print(f"{name:<30} {m_val:>10} {d_val:>10} {winner:>10} {str(margin):>10}")

print(f"{'Total Operations':<30} {'150':>10} {'150':>10}")

# Operation breakdown
print(f"\n--- Part 1: Operation-Specific Breakdown ---")
print(f"{'Operation':<15} {'MySQL Avg':>12} {'DynamoDB Avg':>14} {'Faster By':>12}")
print("-" * 55)

for op in ["create_cart", "add_items", "get_cart"]:
    m_op = calc_stats([r for r in mysql if r["operation"] == op])
    d_op = calc_stats([r for r in dynamo if r["operation"] == op])
    diff = round(d_op["avg"] - m_op["avg"], 2)
    if diff > 0:
        faster = f"MySQL by {diff}ms"
    else:
        faster = f"DynamoDB by {abs(diff)}ms"
    print(f"{op.upper():<15} {m_op['avg']:>10} ms {d_op['avg']:>12} ms {faster:>12}")

# Detailed per-operation stats
print(f"\n--- Detailed Per-Operation Stats ---")
for op in ["create_cart", "add_items", "get_cart"]:
    m_op = calc_stats([r for r in mysql if r["operation"] == op])
    d_op = calc_stats([r for r in dynamo if r["operation"] == op])
    print(f"\n{op.upper()}:")
    print(f"  {'':>15} {'MySQL':>10} {'DynamoDB':>10}")
    print(f"  {'Avg':>15} {m_op['avg']:>10} {d_op['avg']:>10}")
    print(f"  {'P50':>15} {m_op['p50']:>10} {d_op['p50']:>10}")
    print(f"  {'P95':>15} {m_op['p95']:>10} {d_op['p95']:>10}")
    print(f"  {'P99':>15} {m_op['p99']:>10} {d_op['p99']:>10}")
    print(f"  {'Min':>15} {m_op['min']:>10} {d_op['min']:>10}")
    print(f"  {'Max':>15} {m_op['max']:>10} {d_op['max']:>10}")
    print(f"  {'Success':>15} {m_op['success']}/{m_op['count']}     {d_op['success']}/{d_op['count']}")

