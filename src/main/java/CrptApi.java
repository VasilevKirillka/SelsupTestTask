import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
@Slf4j
public class CrptApi {
    private final int requestLimit;
    private final TimeUnit timeUnit;
    private final Lock lock;
    private final AtomicInteger requestCount;
    private long lastRequestTime;
    private final ObjectMapper objectMapper;
    private final int valueDuration;
    private final static String URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";

    public CrptApi(TimeUnit timeUnit, int requestLimit, int valueDuration) {
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
        this.valueDuration = valueDuration;
        this.lock = new ReentrantLock();
        this.requestCount = new AtomicInteger(0);
        this.lastRequestTime = System.currentTimeMillis();
        this.objectMapper = new ObjectMapper();
    }
    public void createDocument(Document document, String signature) {
        try {
            lock.lock();
            long currentRequestTime = System.currentTimeMillis();
            long timeLaps = currentRequestTime - lastRequestTime;
            if (timeLaps > timeUnit.toMillis(valueDuration)) {
                requestCount.set(0);
                lastRequestTime = currentRequestTime;
            } else if (requestCount.get() >= requestLimit) {
                log.info("Лимит превышен");
                long sleepTime = timeUnit.toMillis(valueDuration) - timeLaps;
                Thread.sleep(sleepTime);
                lastRequestTime += sleepTime;
                requestCount.set(0);
            }
            CloseableHttpClient httpClient = HttpClients.createDefault();
            HttpPost httpPost = new HttpPost(URL);
            String jsonDocument = objectMapper.writeValueAsString(document);
            httpPost.setEntity(new StringEntity(jsonDocument + signature));

            long startRequestTime = System.currentTimeMillis();
            httpClient.execute(httpPost);
            long endRequestTime = System.currentTimeMillis();
            log.info("Запрос отправлен");
            long requestDuration = endRequestTime - startRequestTime;
            lastRequestTime += requestDuration;
            requestCount.incrementAndGet();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Document {
        private String docId;
        private Description description;
        private String docStatus;
        private String docType;
        private boolean importRequest;
        private String ownInn;
        private String participantInn;
        private String producerInn;
        private String productionDate;
        private String productionType;
        private List<Product> products = new ArrayList<>();
        private String regDate;
        private String regNumber;

    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Description {
        private String participantInn;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Product {
        private String certificateDocument;
        private String certificateDocumentDate;
        private String certificateDocumentNumber;
        private String ownerInn;
        private String producerInn;
        private String productionDate;
        private String tnvedCode;
        private String uitCode;
        private String uituCode;
    }

    public static void main(String[] args) {
        CrptApi api = new CrptApi(TimeUnit.SECONDS, 4, 4);
        String signature = "Подпись";
        Description description = new Description("Description");
        List<Product> products = new ArrayList<>();
        Product product = new Product("certificateDocument", "certificateDocumentDate",
                "2024-02-22", "ownerInn", "producerInn", "2024-02-22",
                "tnvedCode", "uitCode", "uituCode");
        products.add(product);
        Document document = new Document("docId", description, "docStatus", "docType", true, "ownInn", "participantInn",
                "producerInn", "productionDate", "productionType",
                products, "regDate", "regNumber");

        for (int i = 0; i < 15; i++) {
            api.createDocument(document, signature);
        }
    }
}
