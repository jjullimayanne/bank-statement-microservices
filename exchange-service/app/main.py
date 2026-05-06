import os
import json
import logging
import threading
from contextlib import asynccontextmanager
from datetime import datetime, timezone
from decimal import Decimal
from uuid import uuid4

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import PlainTextResponse
from confluent_kafka import Consumer, Producer, KafkaError
from motor.motor_asyncio import AsyncIOMotorClient
from prometheus_client import Counter, Gauge, generate_latest, CONTENT_TYPE_LATEST

exchange_conversions = Counter('exchange_conversions_total', 'Total exchange conversions processed')
exchange_requests = Counter('exchange_requests_total', 'Total exchange requests received', ['status'])
exchange_up = Gauge('up', 'Service health status')
exchange_up.set(1)

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("exchange-service")

KAFKA_BROKERS = os.getenv("KAFKA_BROKERS", "localhost:19092")
MONGO_URI = os.getenv("MONGO_URI", "mongodb://localhost:27017")

EXCHANGE_RATES = {
    ("BRL", "USD"): 0.18,
    ("USD", "BRL"): 5.50,
    ("BRL", "EUR"): 0.16,
    ("EUR", "BRL"): 6.20,
    ("USD", "EUR"): 0.92,
    ("EUR", "USD"): 1.09,
    ("BRL", "GBP"): 0.14,
    ("GBP", "BRL"): 7.10,
    ("USD", "GBP"): 0.79,
    ("GBP", "USD"): 1.27,
    ("EUR", "GBP"): 0.86,
    ("GBP", "EUR"): 1.16,
    ("BRL", "JPY"): 27.0,
    ("JPY", "BRL"): 0.037,
    ("USD", "JPY"): 150.0,
    ("JPY", "USD"): 0.0067,
    ("BRL", "ARS"): 175.0,
    ("ARS", "BRL"): 0.0057,
    ("BRL", "MXN"): 3.10,
    ("MXN", "BRL"): 0.32,
}

producer = Producer({"bootstrap.servers": KAFKA_BROKERS})
mongo_client = None
db = None


def start_kafka_consumer():
    consumer = Consumer({
        "bootstrap.servers": KAFKA_BROKERS,
        "group.id": "exchange-group",
        "auto.offset.reset": "earliest",
    })
    consumer.subscribe(["exchange-rate-request"])

    while True:
        msg = consumer.poll(1.0)
        if msg is None:
            continue
        if msg.error():
            if msg.error().code() != KafkaError._PARTITION_EOF:
                logger.error(f"Kafka error: {msg.error()}")
            continue

        try:
            event = json.loads(msg.value().decode("utf-8"))
            handle_exchange_request(event)
        except Exception as e:
            logger.error(f"Error processing exchange request: {e}")


def handle_exchange_request(event: dict):
    saga_id = event.get("sagaId", "")
    source_currency = event.get("sourceCurrency", "")
    target_currency = event.get("targetCurrency", "")
    source_amount = float(event.get("sourceAmount", 0))

    rate_key = (source_currency, target_currency)
    rate = EXCHANGE_RATES.get(rate_key)

    reply = {
        "eventId": str(uuid4()),
        "sagaId": saga_id,
        "sourceCurrency": source_currency,
        "targetCurrency": target_currency,
        "sourceAmount": source_amount,
        "timestamp": datetime.now(timezone.utc).isoformat(),
    }

    if rate is None:
        reply["success"] = False
        reply["errorMessage"] = f"Exchange rate not available for {source_currency} -> {target_currency}"
        reply["exchangeRate"] = 0
        reply["convertedAmount"] = 0
        exchange_requests.labels(status='failed').inc()
    else:
        converted = round(source_amount * rate, 2)
        reply["success"] = True
        reply["exchangeRate"] = rate
        reply["convertedAmount"] = converted
        exchange_conversions.inc()
        exchange_requests.labels(status='success').inc()

    logger.info(
        f"[EXCHANGE] {source_currency} -> {target_currency}: "
        f"{source_amount} * {rate or 'N/A'} = {reply.get('convertedAmount', 0)}"
    )

    producer.produce(
        "exchange-rate-reply",
        key=saga_id.encode("utf-8"),
        value=json.dumps(reply).encode("utf-8"),
    )
    producer.flush()


@asynccontextmanager
async def lifespan(app: FastAPI):
    global mongo_client, db
    mongo_client = AsyncIOMotorClient(MONGO_URI)
    db = mongo_client["exchange_db"]

    for pair, rate in EXCHANGE_RATES.items():
        await db.exchange_rates.update_one(
            {"sourceCurrency": pair[0], "targetCurrency": pair[1]},
            {"$set": {
                "rate": rate,
                "updatedAt": datetime.now(timezone.utc).isoformat(),
            }},
            upsert=True,
        )

    thread = threading.Thread(target=start_kafka_consumer, daemon=True)
    thread.start()
    logger.info("Exchange service started with Kafka consumer")

    yield

    mongo_client.close()


app = FastAPI(title="Exchange Service", lifespan=lifespan)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/api/exchange/rates")
async def get_all_rates():
    rates = []
    async for rate in db.exchange_rates.find({}, {"_id": 0}):
        rates.append(rate)
    return rates


@app.get("/api/exchange/rate/{source}/{target}")
async def get_rate(source: str, target: str):
    source = source.upper()
    target = target.upper()
    rate = EXCHANGE_RATES.get((source, target))
    if rate is None:
        return {"error": f"Rate not found for {source} -> {target}"}, 404
    return {
        "sourceCurrency": source,
        "targetCurrency": target,
        "rate": rate,
    }


@app.get("/api/exchange/convert/{source}/{target}/{amount}")
async def convert(source: str, target: str, amount: float):
    source = source.upper()
    target = target.upper()
    rate = EXCHANGE_RATES.get((source, target))
    if rate is None:
        return {"error": f"Rate not found for {source} -> {target}"}, 404
    converted = round(amount * rate, 2)
    return {
        "sourceCurrency": source,
        "targetCurrency": target,
        "sourceAmount": amount,
        "rate": rate,
        "convertedAmount": converted,
    }


@app.get("/api/exchange/health")
async def health():
    return {"status": "UP", "service": "exchange-service"}


@app.get("/metrics")
async def metrics():
    return PlainTextResponse(generate_latest(), media_type=CONTENT_TYPE_LATEST)
