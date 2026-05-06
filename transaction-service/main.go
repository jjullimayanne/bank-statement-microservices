package main

import (
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"
	"time"

	"github.com/confluentinc/confluent-kafka-go/v2/kafka"
	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
)

type TransactionRequest struct {
	SourceAccountID string  `json:"sourceAccountId" binding:"required"`
	TargetAccountID string  `json:"targetAccountId"`
	TransactionType string  `json:"transactionType" binding:"required"`
	Amount          float64 `json:"amount" binding:"required,gt=0"`
	Currency        string  `json:"currency" binding:"required"`
	TargetCurrency  string  `json:"targetCurrency"`
	Description     string  `json:"description"`
}

type TransactionEvent struct {
	EventID         string  `json:"eventId"`
	SagaID          string  `json:"sagaId"`
	SourceAccountID string  `json:"sourceAccountId"`
	TargetAccountID string  `json:"targetAccountId"`
	TransactionType string  `json:"transactionType"`
	Amount          float64 `json:"amount"`
	Currency        string  `json:"currency"`
	TargetCurrency  string  `json:"targetCurrency"`
	Description     string  `json:"description"`
	Timestamp       string  `json:"timestamp"`
	IdempotencyKey  string  `json:"idempotencyKey"`
}

var producer *kafka.Producer

func main() {
	brokers := getEnv("KAFKA_BROKERS", "localhost:19092")
	port := getEnv("PORT", "8083")

	var err error
	producer, err = kafka.NewProducer(&kafka.ConfigMap{
		"bootstrap.servers": brokers,
	})
	if err != nil {
		log.Fatalf("Failed to create Kafka producer: %v", err)
	}
	defer producer.Close()

	go func() {
		for e := range producer.Events() {
			switch ev := e.(type) {
			case *kafka.Message:
				if ev.TopicPartition.Error != nil {
					log.Printf("Delivery failed: %v", ev.TopicPartition.Error)
				} else {
					log.Printf("Delivered to %v [%d] at offset %v",
						*ev.TopicPartition.Topic, ev.TopicPartition.Partition, ev.TopicPartition.Offset)
				}
			}
		}
	}()

	r := gin.Default()
	r.Use(corsMiddleware())

	r.POST("/api/transactions", handleCreateTransaction)
	r.GET("/api/transactions/health", func(c *gin.Context) {
		c.JSON(http.StatusOK, gin.H{"status": "UP", "service": "transaction-service"})
	})

	log.Printf("Transaction service starting on port %s", port)
	if err := r.Run(":" + port); err != nil {
		log.Fatalf("Failed to start server: %v", err)
	}
}

func handleCreateTransaction(c *gin.Context) {
	var req TransactionRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	event := TransactionEvent{
		EventID:         uuid.New().String(),
		SourceAccountID: req.SourceAccountID,
		TargetAccountID: req.TargetAccountID,
		TransactionType: req.TransactionType,
		Amount:          req.Amount,
		Currency:        req.Currency,
		TargetCurrency:  req.TargetCurrency,
		Description:     req.Description,
		Timestamp:       time.Now().UTC().Format(time.RFC3339Nano),
		IdempotencyKey:  uuid.New().String(),
	}

	eventJSON, err := json.Marshal(event)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to serialize event"})
		return
	}

	topic := "transaction-events"
	err = producer.Produce(&kafka.Message{
		TopicPartition: kafka.TopicPartition{Topic: &topic, Partition: kafka.PartitionAny},
		Key:            []byte(event.SourceAccountID),
		Value:          eventJSON,
	}, nil)

	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": fmt.Sprintf("Failed to publish event: %v", err)})
		return
	}

	producer.Flush(5000)

	log.Printf("[TRANSACTION] Published event %s: %s %v %s from %s",
		event.EventID, event.TransactionType, event.Amount, event.Currency, event.SourceAccountID)

	c.JSON(http.StatusAccepted, gin.H{
		"eventId":   event.EventID,
		"status":    "ACCEPTED",
		"message":   "Transaction event published to broker",
		"timestamp": event.Timestamp,
	})
}

func corsMiddleware() gin.HandlerFunc {
	return func(c *gin.Context) {
		c.Writer.Header().Set("Access-Control-Allow-Origin", "*")
		c.Writer.Header().Set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
		c.Writer.Header().Set("Access-Control-Allow-Headers", "Content-Type, Authorization")
		if c.Request.Method == "OPTIONS" {
			c.AbortWithStatus(204)
			return
		}
		c.Next()
	}
}

func getEnv(key, fallback string) string {
	if value, ok := os.LookupEnv(key); ok {
		return value
	}
	return fallback
}
