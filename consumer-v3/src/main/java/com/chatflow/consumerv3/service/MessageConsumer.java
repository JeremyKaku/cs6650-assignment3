package com.chatflow.consumerv3.service;

import com.chatflow.consumerv3.models.QueueMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Consumer with database persistence and server broadcast (Assignment 3).
 * Reads from SQS, writes to DynamoDB, and calls Server API to broadcast.
 */
@Service
public class MessageConsumer {

  private static final Logger logger = LoggerFactory.getLogger(MessageConsumer.class);
  private static final int MAX_MESSAGES_PER_POLL = 10;
  private static final int WAIT_TIME_SECONDS = 20;

  @Value("${consumer.thread.count:20}")
  private int consumerThreadCount;

  @Value("${aws.region:us-west-2}")
  private String awsRegion;

  @Value("${sqs.queue.url.prefix}")
  private String queueUrlPrefix;

  @Value("${sqs.rooms.start:1}")
  private int roomsStart;

  @Value("${sqs.rooms.end:20}")
  private int roomsEnd;

  @Value("${server.broadcast.url}")
  private String serverBroadcastUrl;

  private final DynamoDBService dynamoDBService;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final HttpClient httpClient = HttpClient.newBuilder()
      .connectTimeout(java.time.Duration.ofSeconds(5))
      .build();

  private SqsClient sqsClient;
  private ExecutorService executorService;
  private final AtomicBoolean running = new AtomicBoolean(false);
  private final AtomicLong messagesProcessed = new AtomicLong(0);
  private final AtomicLong messagesConsumed = new AtomicLong(0);
  private final AtomicLong messagesFailed = new AtomicLong(0);
  private final AtomicLong broadcastsFailed = new AtomicLong(0);

  @Autowired
  public MessageConsumer(DynamoDBService dynamoDBService) {
    this.dynamoDBService = dynamoDBService;
  }

  @PostConstruct
  public void start() {
    logger.info("Starting Message Consumer with {} threads", consumerThreadCount);
    logger.info("Server broadcast URL: {}", serverBroadcastUrl);

    sqsClient = SqsClient.builder()
        .region(Region.of(awsRegion))
        .credentialsProvider(DefaultCredentialsProvider.create())
        .build();

    executorService = Executors.newFixedThreadPool(consumerThreadCount,
        new ThreadFactory() {
          private int counter = 0;
          @Override
          public Thread newThread(Runnable r) {
            return new Thread(r, "consumer-thread-" + (++counter));
          }
        });

    running.set(true);

    List<String> queueUrls = generateQueueUrls();
    int queuesPerThread = (int) Math.ceil((double) queueUrls.size() / consumerThreadCount);

    for (int i = 0; i < consumerThreadCount; i++) {
      int start = i * queuesPerThread;
      int end = Math.min(start + queuesPerThread, queueUrls.size());

      if (start < queueUrls.size()) {
        List<String> assignedQueues = queueUrls.subList(start, end);
        executorService.submit(new ConsumerTask(assignedQueues));
        logger.info("Thread {} assigned queues: {}", i, assignedQueues);
      }
    }

    logger.info("Message Consumer started successfully");
  }

  @PreDestroy
  public void stop() {
    logger.info("Stopping Message Consumer");
    running.set(false);

    if (executorService != null) {
      executorService.shutdown();
      try {
        if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
          executorService.shutdownNow();
        }
      } catch (InterruptedException e) {
        executorService.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }

    if (sqsClient != null) {
      sqsClient.close();
    }

    logger.info("Message Consumer stopped. Processed: {}, Failed: {}, Broadcast Failures: {}",
        messagesProcessed.get(), messagesFailed.get(), broadcastsFailed.get());
  }

  private List<String> generateQueueUrls() {
    List<String> urls = new CopyOnWriteArrayList<>();
    for (int i = roomsStart; i <= roomsEnd; i++) {
      urls.add(queueUrlPrefix + "room-" + i + ".fifo");
    }
    return urls;
  }

  private class ConsumerTask implements Runnable {
    private final List<String> queueUrls;

    public ConsumerTask(List<String> queueUrls) {
      this.queueUrls = queueUrls;
    }

    @Override
    public void run() {
      logger.info("Consumer task started for queues: {}", queueUrls);

      while (running.get()) {
        for (String queueUrl : queueUrls) {
          try {
            pollAndProcess(queueUrl);
          } catch (Exception e) {
            logger.error("Error polling queue {}: {}", queueUrl, e.getMessage());
          }
        }
      }

      logger.info("Consumer task stopped for queues: {}", queueUrls);
    }

    private void pollAndProcess(String queueUrl) {
      try {
        ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
            .queueUrl(queueUrl)
            .maxNumberOfMessages(MAX_MESSAGES_PER_POLL)
            .waitTimeSeconds(WAIT_TIME_SECONDS)
            .build();

        ReceiveMessageResponse response = sqsClient.receiveMessage(receiveRequest);
        List<Message> messages = response.messages();

        if (messages.isEmpty()) {
          return;
        }

        logger.debug("Received {} messages from queue {}", messages.size(), queueUrl);

        for (Message message : messages) {
          processMessage(queueUrl, message);
        }

      } catch (QueueDoesNotExistException e) {
        logger.error("Queue does not exist: {}", queueUrl);
        running.set(false);
      } catch (Exception e) {
        logger.error("Error receiving messages from {}: {}", queueUrl, e.getMessage());
      }
    }

    private void processMessage(String queueUrl, Message message) {
      long startTime = System.nanoTime();

      try {
        QueueMessage queueMessage = objectMapper.readValue(
            message.body(), QueueMessage.class);

        // STEP 1: Write to DynamoDB (async, non-blocking)
        boolean dbQueued = dynamoDBService.queueMessageForWrite(queueMessage);

        if (!dbQueued) {
          logger.warn("Failed to queue message {} for DB write",
              queueMessage.getMessageId());
        }

        // STEP 2: Call Server to broadcast (synchronous but fast)
        boolean broadcasted = notifyServerToBroadcast(queueMessage);

        if (broadcasted) {
          // Success - delete from queue
          deleteMessage(queueUrl, message.receiptHandle());
          messagesConsumed.incrementAndGet();
          messagesProcessed.incrementAndGet();

          logger.debug("Processed message {} for room {}",
              queueMessage.getMessageId(), queueMessage.getRoomId());
        } else {
          // Broadcast failed but don't fail the entire operation
          // Message will be retried based on SQS visibility timeout
          broadcastsFailed.incrementAndGet();
          logger.warn("Failed to broadcast message {}, will retry",
              queueMessage.getMessageId());
        }

      } catch (Exception e) {
        messagesFailed.incrementAndGet();
        logger.error("Error processing message from {}: {}", queueUrl, e.getMessage());
      } finally {
        long endTime = System.nanoTime();
        long processingTime = (endTime - startTime) / 1_000_000;
        if (processingTime > 100) {
          logger.warn("Slow message processing: {}ms", processingTime);
        }
      }
    }

    /**
     * Notify Server to broadcast message to room via HTTP API
     */
    private boolean notifyServerToBroadcast(QueueMessage queueMessage) {
      try {
        String requestBody = objectMapper.writeValueAsString(queueMessage);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(serverBroadcastUrl))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .timeout(java.time.Duration.ofSeconds(5))
            .build();

        HttpResponse<String> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
          logger.debug("Server broadcast successful for message {}",
              queueMessage.getMessageId());
          return true;
        } else {
          logger.warn("Server broadcast API returned status {} for message {}: {}",
              response.statusCode(), queueMessage.getMessageId(), response.body());
          return false;
        }

      } catch (java.net.ConnectException e) {
        logger.error("Cannot connect to server broadcast API at {}: {}",
            serverBroadcastUrl, e.getMessage());
        return false;
      } catch (Exception e) {
        logger.error("Failed to call server broadcast API for message {}: {}",
            queueMessage.getMessageId(), e.getMessage());
        return false;
      }
    }

    private void deleteMessage(String queueUrl, String receiptHandle) {
      try {
        DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
            .queueUrl(queueUrl)
            .receiptHandle(receiptHandle)
            .build();

        sqsClient.deleteMessage(deleteRequest);
      } catch (Exception e) {
        logger.error("Failed to delete message: {}", e.getMessage());
      }
    }
  }

  public String getStats() {
    return String.format("Running: %s, Processed: %d, Failed: %d, Broadcast Failures: %d, DB Buffer: %d",
        running.get(), messagesProcessed.get(), messagesFailed.get(),
        broadcastsFailed.get(), dynamoDBService.getBufferSize());
  }
}