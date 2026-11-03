package com.onlinestore.product;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.onlinestore.product.model.Product;

class ProductStoreTest {

    private final ProductStore store = new ProductStore(100_000);

    @Test
    void generatesTheSameCatalogueEveryTime() {
        assertThat(store.searchOrder()).hasSize(100_000);
        assertThat(store.get(1)).contains(new Product(1, "Product Beta 1", "Books",
                "Premium grade item with warranty", "Beta", "SKU-000001", "Beta", 1, 2, 2));
        assertThat(store.get(8)).contains(new Product(8, "Product Alpha 8", "Electronics",
                "High quality product for everyday use", "Alpha", "SKU-000008", "Alpha", 0, 9, 9));
        assertThat(store.get(100_000)).contains(new Product(100_000, "Product Alpha 100000", "Electronics",
                "High quality product for everyday use", "Alpha", "SKU-100000", "Alpha", 0, 1, 1));
        assertThat(store.get(0)).isEmpty();
        assertThat(store.get(100_001)).isEmpty();
    }

    @Test
    void addedProductsCanBeFetchedButAreNotSearched() {
        store.put(new Product(200_000, "", "", "", "", "SKU-X", "Acme", 0, 0, 0));

        assertThat(store.get(200_000)).isPresent();
        assertThat(store.searchOrder()).doesNotContain(200_000L);
    }
}
