import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CrptApi {
    private final long timeInterval;
    private final int requestLimit;
    private final Semaphore semaphore;
    private LocalDateTime lastUpdate;

    public CrptApi(
            TimeUnit timeUnit,
            int requestLimit
    ) {
        this.timeInterval = timeUnit.toMillis(1);
        this.requestLimit = requestLimit;
        lastUpdate = LocalDateTime.now();
        semaphore = new Semaphore(requestLimit, true);
    }

    public HttpResponse<String> createDocument(Document document, String signature)
            throws InterruptedException, IOException {
        if (!checkLimit()) {
            System.out.println("WARN: You have exceeded request limit. Try again later");
        }

        HttpClient client = HttpClient.newHttpClient();
        ObjectMapper objectMapper = new ObjectMapper();
        HttpRequest request = HttpRequest.newBuilder()
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Signature", signature) // не понял куда ее деть(
                .POST(HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(document)
                ))
                .build();

        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) {
            throw new RuntimeException("API request failed = " + response.statusCode());
        }
        return response;
    }

    private synchronized boolean checkLimit() throws InterruptedException {
        if (Duration.between(lastUpdate, LocalDateTime.now()).toMillis() >= timeInterval) {
            lastUpdate = LocalDateTime.now();
            semaphore.release(requestLimit - semaphore.availablePermits());
            return true;
        }

        semaphore.acquire();
        return semaphore.availablePermits() > 0;
    }

    public record Document(
            Description description,
            String docId,
            String docStatus,
            String docType,
            int importRequest,
            String ownerInn,
            String participantInn,
            String producerInn,
            LocalDate productionDate,
            String productionType,
            List<Product> products,
            LocalDate regDate,
            String regNumber
    ) {
        public record Description(String participantInn) {}

        public record Product(
                String certificateDocument,
                LocalDate certificateDocumentDate,
                String certificateDocumentNumber,
                String ownerInn,
                String producerInn,
                LocalDate productionDate,
                String tnvedCode,
                String uitCode,
                String uituCode
        ) {}
    }

}
