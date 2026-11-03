from locust import HttpUser, FastHttpUser, task, between
import random

# # --- Standard HttpUser ---
# class ProductUser(HttpUser):
#     wait_time = between(1, 3)

#     @task(3)  # Read-heavy (more GETs than POSTs)
#     def get_product(self):
#         pid = random.randint(1, 10000)
#         with self.client.get(f"/products/{pid}", name="/products/:id", catch_response=True) as response:
#             if response.status_code == 404:
#                 response.success()  # 404 is expected for non-existent products

#     @task(1)
#     def add_product(self):
#         pid = random.randint(1, 10000)
#         self.client.post(f"/products/{pid}/details", json={
#             "product_id": pid,
#             "sku": f"SKU-{pid}",
#             "manufacturer": "Acme Corporation",
#             "category_id": random.randint(1, 50),
#             "weight": random.randint(100, 5000),
#             "some_other_id": random.randint(1, 1000)
#         }, name="/products/:id/details")


# --- FastHttpUser  ---
# class ProductFastUser(FastHttpUser):
    # wait_time = between(1, 3)

    # @task(3)
    # def get_product(self):
    #     pid = random.randint(1, 10000)
    #     with self.client.get(f"/products/{pid}", name="/products/:id", catch_response=True) as response:
    #         if response.status_code == 404:
    #             response.success()

    # @task(1)
    # def add_product(self):
    #     pid = random.randint(1, 10000)
    #     self.client.post(f"/products/{pid}/details", json={
    #         "product_id": pid,
    #         "sku": f"SKU-{pid}",
    #         "manufacturer": "Acme Corporation",
    #         "category_id": random.randint(1, 50),
    #         "weight": random.randint(100, 5000),
    #         "some_other_id": random.randint(1, 1000)
    #     }, name="/products/:id/details")

    # --- This Week: Search Load Test ---
class ProductSearchUser(FastHttpUser):
    wait_time = between(0, 0)

    @task(3)
    def search_alpha(self):
        self.client.get("/products/search?q=alpha")

    @task(3)
    def search_electronics(self):
        self.client.get("/products/search?q=electronics")

    @task(2)
    def search_beta(self):
        self.client.get("/products/search?q=beta")

    @task(1)
    def search_home(self):
        self.client.get("/products/search?q=home")

    @task(1)
    def search_sports(self):
        self.client.get("/products/search?q=sports")