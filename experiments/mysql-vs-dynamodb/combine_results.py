import json

with open("mysql_test_results.json") as f:
    mysql = json.load(f)

with open("dynamodb_test_results.json") as f:
    dynamo = json.load(f)

# Add database label to each entry
for r in mysql:
    r["database"] = "mysql"
for r in dynamo:
    r["database"] = "dynamodb"

combined = mysql + dynamo

with open("combined_results.json", "w") as f:
    json.dump(combined, f, indent=2)

print(f"MySQL operations: {len(mysql)}")
print(f"DynamoDB operations: {len(dynamo)}")
print(f"Combined total: {len(combined)}")
print("Saved to combined_results.json")