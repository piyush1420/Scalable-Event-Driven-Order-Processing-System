"""Shared setup for the API tests.

All tests talk to the store through its front door, so the same tests run against
  - the local stack:  docker compose up          (default STORE_URL=http://localhost:8000)
  - AWS:              STORE_URL=$(terraform -chdir=terraform output -raw store_url) pytest tests
"""
import os
import time

import pytest
import requests

STORE_URL = os.environ.get("STORE_URL", "http://localhost:8000").rstrip("/")
TIMEOUT = 30


class Store:
    """Tiny HTTP helper bound to the store's base URL."""

    def __init__(self, base_url):
        self.base_url = base_url

    def get(self, path, **kwargs):
        return requests.get(self.base_url + path, timeout=TIMEOUT, **kwargs)

    def post(self, path, json=None, data=None, **kwargs):
        headers = {"Content-Type": "application/json"} if data is not None else None
        return requests.post(self.base_url + path, json=json, data=data, headers=headers, timeout=TIMEOUT, **kwargs)

    def create_cart(self, customer_id=1):
        response = self.post("/shopping-carts", json={"customer_id": customer_id})
        assert response.status_code == 201, response.text
        return response.json()["shopping_cart_id"]


@pytest.fixture(scope="session")
def store():
    """The store, once its front door answers (waits up to 2 minutes for a fresh stack)."""
    deadline = time.time() + 120
    while True:
        try:
            if requests.get(STORE_URL + "/products/1", timeout=5).status_code == 200:
                return Store(STORE_URL)
        except requests.RequestException:
            pass
        if time.time() > deadline:
            pytest.exit("Store not reachable at {} - start it with `docker compose up` "
                        "or set STORE_URL".format(STORE_URL), returncode=2)
        time.sleep(2)


def error(code, message):
    """Error body used by the product and cart services."""
    return {"error": code, "message": message}
