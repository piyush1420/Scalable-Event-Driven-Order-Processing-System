"""API contract: status codes and error messages of every endpoint.

These are the exact answers of the original implementation (they were compared request by request
while porting), so a change that breaks a client shows up here.
"""
import re

import pytest

from conftest import error

UUID = re.compile(r"^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
ISO_UTC = re.compile(r"^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(\.\d+)?Z$")


# ── Products ──

@pytest.mark.parametrize("product_id", ["abc", "0", "-1", "1.5"])
def test_invalid_product_ids_are_rejected(store, product_id):
    response = store.get("/products/" + product_id)

    assert response.status_code == 400
    assert response.json() == error("INVALID_INPUT", "Invalid product ID")


def test_unknown_product_is_not_found(store):
    response = store.get("/products/999999")

    assert response.status_code == 404
    assert response.json() == error("NOT_FOUND", "No product found with ID 999999")


def test_product_has_catalogue_and_stock_fields(store):
    body = store.get("/products/8").json()

    assert body["sku"] == "SKU-000008"
    assert body["category"] == "Electronics"
    assert set(body) >= {"id", "name", "brand", "manufacturer", "category_id", "weight", "some_other_id",
                         "stock", "warehouse", "inventory_status"}


def test_search_requires_a_query(store):
    for params in ({}, {"q": ""}):
        response = store.get("/products/search", params=params)
        assert response.status_code == 400
        assert response.json() == error("INVALID_INPUT", "Query parameter 'q' is required")


def test_search_checks_the_first_101_products_and_returns_at_most_20(store):
    body = store.get("/products/search", params={"q": "product"}).json()

    assert body["total_found"] == 101
    assert len(body["products"]) == 20
    assert body["search_time"]


@pytest.mark.parametrize("body, message", [
    ('{"manufacturer": "Acme"}', "SKU is required"),
    ('{"sku": "SKU-1"}', "Manufacturer is required"),
])
def test_product_details_require_sku_and_manufacturer(store, body, message):
    response = store.post("/products/900001/details", data=body)

    assert response.status_code == 400
    assert response.json() == error("INVALID_INPUT", message)


@pytest.mark.parametrize("body", ["", "{bad", '{"sku": 5, "manufacturer": "Acme"}', '{"weight": "5"}'])
def test_unreadable_product_details_are_rejected(store, body):
    response = store.post("/products/900001/details", data=body)

    assert response.status_code == 400
    assert response.json()["message"] == "Invalid input data"
    assert response.json()["details"]


def test_product_details_are_stored(store):
    response = store.post("/products/900002/details",
                          json={"sku": "SKU-900002", "manufacturer": "Acme", "name": "Test Widget", "weight": 3})
    assert response.status_code == 204

    body = store.get("/products/900002").json()
    assert (body["id"], body["name"], body["sku"], body["weight"]) == (900002, "Test Widget", "SKU-900002", 3)


# ── Shopping carts ──

@pytest.mark.parametrize("body", ["", "{bad", "[]", '{"customer_id": "5"}', '{"customer_id": 1.5}'])
def test_unreadable_cart_bodies_are_rejected(store, body):
    response = store.post("/shopping-carts", data=body)

    assert response.status_code == 400
    assert response.json() == error("INVALID_INPUT", "Invalid request body")


@pytest.mark.parametrize("body", ['{"customer_id": 0}', '{"customer_id": -3}', "{}", "null"])
def test_customer_id_must_be_positive(store, body):
    response = store.post("/shopping-carts", data=body)

    assert response.status_code == 400
    assert response.json() == error("INVALID_INPUT", "customer_id must be a positive integer")


def test_missing_cart_is_not_found(store):
    response = store.get("/shopping-carts/does-not-exist")

    assert response.status_code == 404
    assert response.json() == error("NOT_FOUND", "Cart does-not-exist not found")


def test_new_cart_is_empty(store):
    cart_id = store.create_cart(customer_id=77)

    body = store.get("/shopping-carts/{}".format(cart_id)).json()
    assert body["shopping_cart_id"] == cart_id
    assert body["customer_id"] == 77
    assert body["items"] == []
    assert ISO_UTC.match(body["created_at"])


@pytest.mark.parametrize("body, message", [
    ("{bad", "Invalid request body"),
    ('{"product_id": 0, "quantity": 1}', "product_id and quantity must be positive integers"),
    ('{"product_id": 1}', "product_id and quantity must be positive integers"),
])
def test_add_item_validation(store, body, message):
    cart_id = store.create_cart()

    response = store.post("/shopping-carts/{}/items".format(cart_id), data=body)

    assert response.status_code == 400
    assert response.json() == error("INVALID_INPUT", message)


def test_adding_to_a_missing_cart_is_not_found(store):
    response = store.post("/shopping-carts/does-not-exist/items", json={"product_id": 1, "quantity": 1})

    assert response.status_code == 404
    assert response.json() == error("NOT_FOUND", "Cart does-not-exist not found")


# ── Orders ──

@pytest.mark.parametrize("path", ["/orders/sync", "/orders/async"])
@pytest.mark.parametrize("body", ["", "{bad", '{"customer_id": "5"}', '{"items": {}}',
                                  '{"items": [{"price": "9.99"}]}'])
def test_unreadable_orders_are_rejected(store, path, body):
    response = store.post(path, data=body)

    assert response.status_code == 400
    assert response.json() == {"error": "Invalid request body"}


def test_async_order_is_accepted_immediately(store):
    order = {"customer_id": 42, "items": [{"product_id": "PROD-1", "name": "Item-1", "quantity": 2, "price": 19.99}]}

    response = store.post("/orders/async", json=order)

    assert response.status_code == 202
    body = response.json()
    assert UUID.match(body["order_id"])
    assert (body["customer_id"], body["status"], body["items"]) == (42, "pending", order["items"])
    assert ISO_UTC.match(body["created_at"])
    assert response.elapsed.total_seconds() < 2
