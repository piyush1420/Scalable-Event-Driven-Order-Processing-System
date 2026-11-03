package com.onlinestore.product;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.onlinestore.product.model.Product;

/**
 * In-memory product catalogue, generated at startup. Search walks the generated IDs in order;
 * products added later through the details endpoint can be fetched by ID but are not searched.
 */
public class ProductStore {

    private static final Logger log = LoggerFactory.getLogger(ProductStore.class);

    private static final String[] BRANDS = {"Alpha", "Beta", "Gamma", "Delta", "Epsilon", "Zeta", "Eta", "Theta"};
    private static final String[] CATEGORIES = {"Electronics", "Books", "Home", "Sports", "Toys", "Clothing", "Food",
            "Health"};
    private static final String[] DESCRIPTIONS = {
            "High quality product for everyday use",
            "Premium grade item with warranty",
            "Budget friendly option for all",
            "Top rated by customers worldwide",
            "Best seller in its category",
            "New arrival with modern features",
            "Classic design with reliable performance",
            "Eco-friendly and sustainable choice",
    };

    private final Map<Long, Product> products = new ConcurrentHashMap<>();
    private final long[] searchOrder;

    public ProductStore(int count) {
        log.info("Generating {} products...", String.format(Locale.ROOT, "%,d", count));
        searchOrder = new long[count];
        for (int i = 1; i <= count; i++) {
            String brand = BRANDS[i % BRANDS.length];
            Product product = new Product(
                    i,
                    "Product " + brand + " " + i,
                    CATEGORIES[i % CATEGORIES.length],
                    DESCRIPTIONS[i % DESCRIPTIONS.length],
                    brand,
                    String.format(Locale.ROOT, "SKU-%06d", i),
                    brand,
                    i % CATEGORIES.length,
                    (i % 50) + 1,
                    (i % 100) + 1);
            products.put((long) i, product);
            searchOrder[i - 1] = i;
        }
        log.info("Generated {} products", searchOrder.length);
    }

    public Optional<Product> get(long id) {
        return Optional.ofNullable(products.get(id));
    }

    public void put(Product product) {
        products.put(product.id(), product);
    }

    /** IDs of the generated products, in the order search visits them. */
    public long[] searchOrder() {
        return searchOrder;
    }
}
