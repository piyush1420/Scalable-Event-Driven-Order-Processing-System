# Single table design: cart_id is the partition key.
# No sort key is needed since the whole cart is fetched by ID.
resource "aws_dynamodb_table" "shopping_carts" {
  name         = "${var.service_name}-shopping-carts"
  billing_mode = "PAY_PER_REQUEST" # no capacity planning needed, scales automatically
  hash_key     = "cart_id"

  attribute {
    name = "cart_id"
    type = "S" # string type for UUID-based cart IDs
  }

  tags = {
    Name = "${var.service_name}-shopping-carts"
  }
}
