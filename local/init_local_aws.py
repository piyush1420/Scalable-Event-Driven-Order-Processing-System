"""Create, inside the local AWS emulator (moto), the resources Terraform creates on real AWS.

    SNS topic  online-store-order-processing-events
      -> SQS queue online-store-order-processing-queue   (SNS delivers every order here)
    DynamoDB table online-store-shopping-carts           (partition key cart_id, a string)

Run by docker compose (service "aws-init"); safe to run more than once.
"""
import os
import time

import boto3
from botocore.exceptions import ClientError, EndpointConnectionError

ENDPOINT = os.environ.get("AWS_ENDPOINT_URL", "http://aws:5000")
REGION = os.environ.get("AWS_REGION", "us-east-1")
PREFIX = "online-store"


def client(service):
    return boto3.client(service, endpoint_url=ENDPOINT, region_name=REGION,
                        aws_access_key_id="test", aws_secret_access_key="test")


def wait_for_emulator(sns):
    for _ in range(30):
        try:
            sns.list_topics()
            return
        except EndpointConnectionError:
            time.sleep(1)
    raise SystemExit(f"AWS emulator not reachable at {ENDPOINT}")


def main():
    sns, sqs, dynamodb = client("sns"), client("sqs"), client("dynamodb")
    wait_for_emulator(sns)

    topic_arn = sns.create_topic(Name=f"{PREFIX}-order-processing-events")["TopicArn"]
    queue_url = sqs.create_queue(
        QueueName=f"{PREFIX}-order-processing-queue",
        Attributes={"VisibilityTimeout": "30", "MessageRetentionPeriod": "345600",
                    "ReceiveMessageWaitTimeSeconds": "20"},
    )["QueueUrl"]
    queue_arn = sqs.get_queue_attributes(QueueUrl=queue_url, AttributeNames=["QueueArn"])["Attributes"]["QueueArn"]
    sns.subscribe(TopicArn=topic_arn, Protocol="sqs", Endpoint=queue_arn)

    table = f"{PREFIX}-shopping-carts"
    try:
        dynamodb.create_table(
            TableName=table,
            BillingMode="PAY_PER_REQUEST",
            KeySchema=[{"AttributeName": "cart_id", "KeyType": "HASH"}],
            AttributeDefinitions=[{"AttributeName": "cart_id", "AttributeType": "S"}],
        )
    except ClientError as error:
        if error.response["Error"]["Code"] != "ResourceInUseException":
            raise

    print(f"topic  {topic_arn}")
    print(f"queue  {queue_url}")
    print(f"table  {table}")


if __name__ == "__main__":
    main()
