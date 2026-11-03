import os
import requests
import json
import time
from datetime import datetime, timezone

# Set BASE_URL to your cart service (e.g. http://localhost:8082); default is the original HW8 ALB
BASE_URL = os.environ.get("BASE_URL", "http://hw8-dynamo-alb-1999093070.us-east-1.elb.amazonaws.com")
RESULTS = []

def record(operation, response, elapsed_ms):
    RESULTS.append({
        "operation": operation,
        "response_time": round(elapsed_ms, 2),
        "success": 200 <= response.status_code < 300,
        "status_code": response.status_code,
        "timestamp": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    })

# ── Phase 1: Create 50 carts ──
print("Phase 1: Creating 50 carts...")
cart_ids = []
for i in range(50):
    start = time.time()
    resp = requests.post(f"{BASE_URL}/shopping-carts",
                         json={"customer_id": i + 1})
    elapsed = (time.time() - start) * 1000
    record("create_cart", resp, elapsed)

    if resp.status_code == 201:
        cart_ids.append(resp.json()["shopping_cart_id"])

    if (i + 1) % 10 == 0:
        print(f"  Created {i + 1}/50 carts")

# ── Phase 2: Add items to 50 carts ──
print("Phase 2: Adding items to 50 carts...")
for i in range(50):
    cart_id = cart_ids[i % len(cart_ids)]
    start = time.time()
    resp = requests.post(f"{BASE_URL}/shopping-carts/{cart_id}/items",
                         json={"product_id": i + 1, "quantity": (i % 5) + 1})
    elapsed = (time.time() - start) * 1000
    record("add_items", resp, elapsed)

    if (i + 1) % 10 == 0:
        print(f"  Added items {i + 1}/50")

# ── Phase 3: Get 50 carts ──
print("Phase 3: Retrieving 50 carts...")
for i in range(50):
    cart_id = cart_ids[i % len(cart_ids)]
    start = time.time()
    resp = requests.get(f"{BASE_URL}/shopping-carts/{cart_id}")
    elapsed = (time.time() - start) * 1000
    record("get_cart", resp, elapsed)

    if (i + 1) % 10 == 0:
        print(f"  Retrieved {i + 1}/50 carts")

# ── Save results ──
with open("dynamodb_test_results.json", "w") as f:
    json.dump(RESULTS, f, indent=2)

# ── Print summary ──
print("\n" + "=" * 50)
print("TEST SUMMARY")
print("=" * 50)

for op in ["create_cart", "add_items", "get_cart"]:
    times = [r["response_time"] for r in RESULTS if r["operation"] == op]
    successes = sum(1 for r in RESULTS if r["operation"] == op and r["success"])
    times.sort()
    print(f"\n{op}:")
    print(f"  Count:    {len(times)}")
    print(f"  Success:  {successes}/{len(times)}")
    print(f"  Avg:      {sum(times)/len(times):.2f} ms")
    print(f"  P50:      {times[len(times)//2]:.2f} ms")
    print(f"  P95:      {times[int(len(times)*0.95)]:.2f} ms")
    print(f"  P99:      {times[int(len(times)*0.99)]:.2f} ms")
    print(f"  Min:      {min(times):.2f} ms")
    print(f"  Max:      {max(times):.2f} ms")

total = len(RESULTS)
total_success = sum(1 for r in RESULTS if r["success"])
print(f"\nTotal: {total_success}/{total} successful")
print(f"Results saved to dynamodb_test_results.json")