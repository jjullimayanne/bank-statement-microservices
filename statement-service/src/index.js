const express = require('express');
const cors = require('cors');
const mongoose = require('mongoose');
const Redis = require('ioredis');
const { Kafka } = require('kafkajs');
const promClient = require('prom-client');

const register = new promClient.Registry();
promClient.collectDefaultMetrics({ register });

const statementEntriesProcessed = new promClient.Counter({
  name: 'statement_entries_processed_total',
  help: 'Total statement entries processed from Kafka',
  registers: [register],
});

const kafkaMessagesConsumed = new promClient.Counter({
  name: 'kafka_messages_consumed_total',
  help: 'Total Kafka messages consumed',
  registers: [register],
});

const cacheHits = new promClient.Counter({
  name: 'cache_hits_total',
  help: 'Total Redis cache hits',
  registers: [register],
});

const cacheMisses = new promClient.Counter({
  name: 'cache_misses_total',
  help: 'Total Redis cache misses',
  registers: [register],
});

const KAFKA_BROKERS = (process.env.KAFKA_BROKERS || 'localhost:19092').split(',');
const MONGO_URI = process.env.MONGO_URI || 'mongodb://localhost:27017/statement_db';
const REDIS_URL = process.env.REDIS_URL || 'redis://localhost:6379';
const PORT = process.env.PORT || 8085;
const CACHE_TTL = 60;

const statementEntrySchema = new mongoose.Schema({
  entryId: { type: String, required: true, unique: true },
  sagaId: String,
  transactionEventId: String,
  accountId: String,
  counterpartyAccountId: String,
  transactionType: String,
  direction: { type: String, enum: ['CREDIT', 'DEBIT'] },
  amount: Number,
  currency: String,
  description: String,
  balanceAfter: Number,
  timestamp: { type: Date, default: Date.now },
  ledgerEntryId: String,
});

statementEntrySchema.index({ accountId: 1, timestamp: -1 });
statementEntrySchema.index({ accountId: 1, currency: 1, timestamp: -1 });

const StatementEntry = mongoose.model('StatementEntry', statementEntrySchema);

const balanceSchema = new mongoose.Schema({
  accountId: { type: String, required: true },
  currency: { type: String, required: true },
  balance: { type: Number, default: 0 },
  lastUpdated: { type: Date, default: Date.now },
});

balanceSchema.index({ accountId: 1, currency: 1 }, { unique: true });

const AccountBalance = mongoose.model('AccountBalance', balanceSchema);

let redis;

async function startKafkaConsumer() {
  const kafka = new Kafka({
    clientId: 'statement-service',
    brokers: KAFKA_BROKERS,
    retry: { retries: 10, initialRetryTime: 3000 },
  });

  const consumer = kafka.consumer({ groupId: 'statement-group' });

  await consumer.connect();
  await consumer.subscribe({ topics: ['ledger-confirmed', 'statement-updates'], fromBeginning: true });

  await consumer.run({
    eachMessage: async ({ topic, message }) => {
      try {
        kafkaMessagesConsumed.inc();
        const event = JSON.parse(message.value.toString());
        await handleLedgerConfirmed(event);
      } catch (err) {
        console.error(`[STATEMENT] Error processing message from ${topic}:`, err.message);
      }
    },
  });

  console.log('[STATEMENT] Kafka consumer started');
}

async function handleLedgerConfirmed(event) {
  const {
    sagaId, transactionEventId, debitAccountId, creditAccountId,
    amount, currency, ledgerEntryId
  } = event;

  const numericAmount = parseFloat(amount) || 0;

  if (debitAccountId && debitAccountId !== 'SYSTEM_TREASURY') {
    const debitEntry = new StatementEntry({
      entryId: `${ledgerEntryId}-debit`,
      sagaId,
      transactionEventId,
      accountId: debitAccountId,
      counterpartyAccountId: creditAccountId,
      transactionType: 'DEBIT',
      direction: 'DEBIT',
      amount: numericAmount,
      currency,
      description: `Débito - Saga ${sagaId}`,
      ledgerEntryId,
    });

    await debitEntry.save();
    await updateBalance(debitAccountId, currency, -numericAmount);
    await invalidateCache(debitAccountId);
  }

  if (creditAccountId && creditAccountId !== 'SYSTEM_TREASURY') {
    const creditEntry = new StatementEntry({
      entryId: `${ledgerEntryId}-credit`,
      sagaId,
      transactionEventId,
      accountId: creditAccountId,
      counterpartyAccountId: debitAccountId,
      transactionType: 'CREDIT',
      direction: 'CREDIT',
      amount: numericAmount,
      currency,
      description: `Crédito - Saga ${sagaId}`,
      ledgerEntryId,
    });

    await creditEntry.save();
    await updateBalance(creditAccountId, currency, numericAmount);
    await invalidateCache(creditAccountId);
  }

  statementEntriesProcessed.inc();
  console.log(`[STATEMENT] Processed ledger entry ${ledgerEntryId} for saga ${sagaId}`);
}

async function updateBalance(accountId, currency, delta) {
  await AccountBalance.findOneAndUpdate(
    { accountId, currency },
    { $inc: { balance: delta }, $set: { lastUpdated: new Date() } },
    { upsert: true }
  );
}

async function invalidateCache(accountId) {
  try {
    const keys = await redis.keys(`statement:${accountId}:*`);
    if (keys.length > 0) {
      await redis.del(...keys);
    }
  } catch (err) {
    console.error(`[STATEMENT] Cache invalidation error:`, err.message);
  }
}

const app = express();
app.use(cors());
app.use(express.json());

app.get('/api/statements/:accountId', async (req, res) => {
  try {
    const { accountId } = req.params;
    const { currency, limit = 50, offset = 0 } = req.query;

    const cacheKey = `statement:${accountId}:${currency || 'all'}:${limit}:${offset}`;
    const cached = await redis.get(cacheKey);
    if (cached) {
      cacheHits.inc();
      return res.json(JSON.parse(cached));
    }
    cacheMisses.inc();

    const filter = { accountId };
    if (currency) filter.currency = currency.toUpperCase();

    const entries = await StatementEntry.find(filter)
      .sort({ timestamp: -1 })
      .skip(parseInt(offset))
      .limit(parseInt(limit))
      .lean();

    const balances = await AccountBalance.find({ accountId }).lean();

    const totalCredits = entries
      .filter(e => e.direction === 'CREDIT')
      .reduce((sum, e) => sum + e.amount, 0);

    const totalDebits = entries
      .filter(e => e.direction === 'DEBIT')
      .reduce((sum, e) => sum + e.amount, 0);

    const response = {
      accountId,
      entries,
      currentBalances: balances.map(b => ({
        currency: b.currency,
        balance: b.balance,
      })),
      totalCredits,
      totalDebits,
      totalEntries: await StatementEntry.countDocuments(filter),
    };

    await redis.setex(cacheKey, CACHE_TTL, JSON.stringify(response));
    res.json(response);
  } catch (err) {
    console.error('[STATEMENT] Error fetching statement:', err.message);
    res.status(500).json({ error: 'Internal server error' });
  }
});

app.get('/api/statements/:accountId/balances', async (req, res) => {
  try {
    const { accountId } = req.params;
    const balances = await AccountBalance.find({ accountId }).lean();
    res.json(balances.map(b => ({ currency: b.currency, balance: b.balance })));
  } catch (err) {
    res.status(500).json({ error: 'Internal server error' });
  }
});

app.get('/api/statements/health', async (req, res) => {
  res.json({ status: 'UP', service: 'statement-service' });
});

app.get('/metrics', async (req, res) => {
  res.set('Content-Type', register.contentType);
  res.end(await register.metrics());
});

async function main() {
  await mongoose.connect(MONGO_URI);
  console.log('[STATEMENT] Connected to MongoDB');

  redis = new Redis(REDIS_URL);
  console.log('[STATEMENT] Connected to Redis');

  await startKafkaConsumer();

  app.listen(PORT, () => {
    console.log(`[STATEMENT] Service running on port ${PORT}`);
  });
}

main().catch(err => {
  console.error('[STATEMENT] Failed to start:', err);
  process.exit(1);
});
