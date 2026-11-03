"""Smoke test: every route behind the front door answers.

Quick check after `docker compose up` or `terraform apply`:  pytest tests/test_smoke.py
"""


def test_product_api_returns_a_product_with_stock(store):
    response = store.get("/products/1")

    assert response.status_code == 200
    body = response.json()
    assert body["name"] == "Product Beta 1"
    assert body["inventory_status"] in ("in_stock", "unavailable")


def test_search_is_routed_to_the_product_api(store):
    response = store.get("/products/search", params={"q": "alpha"})

    assert response.status_code == 200
    assert response.json()["total_found"] == 12


def test_resilience_metrics_are_exposed(store):
    body = store.get("/metrics").json()

    assert set(body) == {"circuit_breaker", "bulkhead"}
    assert body["bulkhead"]["max_concurrent"] == 10


def test_carts_are_routed_to_the_cart_service(store):
    cart_id = store.create_cart(customer_id=5)

    response = store.get("/shopping-carts/{}".format(cart_id))
    assert response.status_code == 200
    assert response.json()["customer_id"] == 5


def test_orders_are_routed_to_the_order_receiver(store):
    response = store.post("/orders/async", json={"customer_id": 1, "items": []})

    assert response.status_code == 202
    assert response.json()["status"] == "pending"


def test_unknown_paths_get_a_json_404(store):
    response = store.get("/no-such-service")

    assert response.status_code == 404
    assert response.json()["error"] == "NOT_FOUND"
