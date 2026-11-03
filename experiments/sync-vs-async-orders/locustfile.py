from locust import HttpUser, task, between
import random


class OrderUser(HttpUser):
    """Sync order testing - Phase 1"""
    wait_time = between(0.1, 0.5)

    def _create_order_payload(self):
        return {
            "customer_id": random.randint(1, 10000),
            "items": [
                {
                    "product_id": f"PROD-{random.randint(100, 999)}",
                    "name": f"Item-{random.randint(1, 50)}",
                    "quantity": random.randint(1, 5),
                    "price": round(random.uniform(9.99, 99.99), 2)
                }
            ]
        }

    @task
    def place_sync_order(self):
        self.client.post(
            "/orders/sync",
            json=self._create_order_payload(),
            name="/orders/sync"
        )


class AsyncOrderUser(HttpUser):
    """Async order testing - Phase 3+"""
    wait_time = between(0.1, 0.5)

    def _create_order_payload(self):
        return {
            "customer_id": random.randint(1, 10000),
            "items": [
                {
                    "product_id": f"PROD-{random.randint(100, 999)}",
                    "name": f"Item-{random.randint(1, 50)}",
                    "quantity": random.randint(1, 5),
                    "price": round(random.uniform(9.99, 99.99), 2)
                }
            ]
        }

    @task
    def place_async_order(self):
        self.client.post(
            "/orders/async",
            json=self._create_order_payload(),
            name="/orders/async"
        )