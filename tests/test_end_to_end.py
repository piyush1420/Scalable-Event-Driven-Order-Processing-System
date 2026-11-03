"""End-to-end: one customer goes through the whole store, using only the front door.

    browse products -> open a cart -> add items -> review the cart -> place the order
"""


def test_customer_journey(store):
    # 1. Browse: search the catalogue and open the first result
    search = store.get("/products/search", params={"q": "electronics"}).json()
    assert search["total_found"] > 0
    first, second = search["products"][0], search["products"][1]

    product = store.get("/products/{}".format(first["id"])).json()
    assert product["category"] == "Electronics"
    assert "stock" in product

    # 2. Open a cart and add two products; adding the same product again adds to its quantity
    cart_id = store.create_cart(customer_id=1001)
    for product_id, quantity in ((first["id"], 1), (second["id"], 1), (first["id"], 2)):
        response = store.post("/shopping-carts/{}/items".format(cart_id),
                              json={"product_id": product_id, "quantity": quantity})
        assert response.status_code == 204

    # 3. Review the cart
    cart = store.get("/shopping-carts/{}".format(cart_id)).json()
    quantities = {item["product_id"]: item["quantity"] for item in cart["items"]}
    assert quantities == {first["id"]: 3, second["id"]: 1}

    # 4. Check out: send the cart's lines to the order service (asynchronous: answered at once)
    lines = [{"product_id": str(item["product_id"]), "name": "Product {}".format(item["product_id"]),
              "quantity": item["quantity"], "price": 9.99} for item in cart["items"]]
    response = store.post("/orders/async", json={"customer_id": cart["customer_id"], "items": lines})

    assert response.status_code == 202
    order = response.json()
    assert order["status"] == "pending"
    assert order["customer_id"] == 1001
    assert sorted(line["product_id"] for line in order["items"]) == sorted(str(p) for p in quantities)


def test_synchronous_checkout_waits_for_payment(store):
    response = store.post("/orders/sync", json={"customer_id": 1002, "items": [
        {"product_id": "8", "name": "Product Alpha 8", "quantity": 1, "price": 9.99}]})

    assert response.status_code == 200
    assert response.json()["status"] == "completed"
    assert response.elapsed.total_seconds() >= 3  # the simulated payment takes 3 seconds
