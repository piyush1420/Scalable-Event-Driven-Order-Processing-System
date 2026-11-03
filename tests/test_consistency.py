"""DynamoDB read-after-write consistency check (Python port of the HW8 test_consistency.sh).

The cart service reads with DynamoDB's default *eventually consistent* reads, so a GET right after
a write may briefly miss it. This file can run two ways:

    python tests/test_consistency.py      # prints the HW8-style report
    pytest tests/test_consistency.py      # checks the cart always becomes consistent

Set STORE_URL to target AWS (default: the local stack at http://localhost:8000).
"""
import time

import requests

from conftest import STORE_URL, TIMEOUT


def create_cart(base, customer_id):
    response = requests.post(base + "/shopping-carts", json={"customer_id": customer_id}, timeout=TIMEOUT)
    response.raise_for_status()
    return response.json()["shopping_cart_id"]


def add_item(base, cart_id, product_id):
    response = requests.post("{}/shopping-carts/{}/items".format(base, cart_id),
                             json={"product_id": product_id, "quantity": 1}, timeout=TIMEOUT)
    response.raise_for_status()


def item_count(base, cart_id):
    """Number of items in the cart, or None if the cart was not found (not visible yet)."""
    response = requests.get("{}/shopping-carts/{}".format(base, cart_id), timeout=TIMEOUT)
    if response.status_code == 404:
        return None
    response.raise_for_status()
    return len(response.json()["items"])


def create_then_read(base, runs=5):
    """Test 1: create a cart, then read it immediately. True = found at once."""
    return [item_count(base, create_cart(base, customer_id=i)) is not None for i in range(1, runs + 1)]


def add_then_read(base, runs=5):
    """Test 2: add one item at a time and read right after. True = all items seen at once."""
    cart_id = create_cart(base, customer_id=100)
    results = []
    for i in range(1, runs + 1):
        add_item(base, cart_id, product_id=i)
        results.append(item_count(base, cart_id) == i)
    return cart_id, results


def rapid_updates(base, count=10):
    """Test 3: add `count` different products back to back, then read once."""
    cart_id = create_cart(base, customer_id=200)
    for i in range(1, count + 1):
        add_item(base, cart_id, product_id=100 + i)
    return cart_id, item_count(base, cart_id)


def eventually(check, seconds=5.0):
    """Polls `check` until it returns True or time runs out."""
    deadline = time.time() + seconds
    while time.time() < deadline:
        if check():
            return True
        time.sleep(0.2)
    return check()


# ── pytest: whatever a single read shows, the data must become consistent ──

def test_created_cart_becomes_readable(store):
    cart_id = create_cart(store.base_url, customer_id=1)
    assert eventually(lambda: item_count(store.base_url, cart_id) == 0)


def test_added_items_become_visible(store):
    cart_id, _ = add_then_read(store.base_url, runs=3)
    assert eventually(lambda: item_count(store.base_url, cart_id) == 3)


def test_rapid_sequential_updates_are_not_lost(store):
    cart_id, _ = rapid_updates(store.base_url, count=10)
    assert eventually(lambda: item_count(store.base_url, cart_id) == 10)


# ── report mode ──

def report(base):
    print("Target: {}\n".format(base))

    print("=== Test 1: Create cart then immediately GET ===")
    for run, found in enumerate(create_then_read(base), 1):
        print("  Run {}: {}".format(run, "CONSISTENT - cart found immediately" if found
                                    else "EVENTUAL CONSISTENCY - cart not found immediately"))

    print("\n=== Test 2: Add item then immediately GET ===")
    cart_id, results = add_then_read(base)
    print("  Cart created: {}".format(cart_id))
    for run, seen in enumerate(results, 1):
        print("  Run {}: {}".format(run, "CONSISTENT - found {0} items (expected {0})".format(run) if seen
                                    else "EVENTUAL CONSISTENCY - fewer than {} items".format(run)))

    print("\n=== Test 3: Rapid updates to same cart ===")
    cart_id, final = rapid_updates(base)
    print("  Cart created: {}".format(cart_id))
    print("  Added 10 items rapidly, found {} items".format(final))
    print("  RESULT: {}".format("CONSISTENT - all 10 items present" if final == 10
                                else "EVENTUAL CONSISTENCY - only {} of 10 items present".format(final)))


if __name__ == "__main__":
    report(STORE_URL)
