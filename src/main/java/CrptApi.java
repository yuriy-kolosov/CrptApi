import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;

import static java.util.concurrent.TimeUnit.*;

public class CrptApi {

    public CrptApi(Semaphore sem) throws InterruptedException {
        Executors.newScheduledThreadPool(1).execute(new CrptApiCreateDocInvoke(sem));
    }

    enum DocumentFormat {
        MANUAL,
        XML,
        CSV
    }

    enum DocumentType {
        LP_INTRODUCE_GOODS,
        LP_INTRODUCE_GOODS_CSV,
        LP_INTRODUCE_GOODS_XML
    }

    enum Production {
        OWN_PRODUCTION,
        CONTRACT_PRODUCTION
    }

    static class SemaphoreServiceDemon implements Runnable {

        private final int requestLimit;
        private final TimeUnit timeUnit;
        private final Semaphore sem;

        public SemaphoreServiceDemon(int requestLimit, TimeUnit timeUnit, Semaphore sem) {
            this.requestLimit = requestLimit;
            this.timeUnit = timeUnit;
            this.sem = sem;
        }

        public void run() {
            for (; ; ) {
                try {
//                    sem.release(requestLimit);
                    Thread.sleep(millisEstimate(timeUnit), nanoEstimate(timeUnit));
                    sem.release(requestLimit);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    /**
     * Method to create document for the introduction of goods produced in the RF into circulation
     *
     * @param docObject document data as java object
     * @param signature digital signature data as string
     * @param sem       semaphore that controls the timing parameters of API access
     * @return document created data as string when request success or empty string otherwise
     * @throws IOException          exception if request was not success
     * @throws URISyntaxException   exception if URI syntax was not right
     * @throws InterruptedException exception if request was interrupted
     */
    public static String createDoc(Object docObject, String signature, Semaphore sem) throws IOException
            , URISyntaxException, InterruptedException {

        final String apiCreateDocUrl = "http://localhost:8080/api/v3/lk/documents/create";    // Demo data
        final String token = "12345";                                                         // Demo data
        final String pg = "lp";                                                               // Demo data

        if (sem.tryAcquire()) {

            ObjectMapper docObjectMapper = new ObjectMapper();
            String docJson = docObjectMapper.writeValueAsString(docObject);
            String docJsonBase64Encoded = Base64.getEncoder().encodeToString(docJson.getBytes());

            JSONObject jsonObjectResult = new JSONObject();
            jsonObjectResult.put("error message", "Ошибка ввода-вывода");

            JSONObject jsonObjectSource = new JSONObject();
            jsonObjectSource.put("document_format", DocumentFormat.MANUAL);
            jsonObjectSource.put("product_document", docJsonBase64Encoded);
            jsonObjectSource.put("document_format", DocumentType.LP_INTRODUCE_GOODS);
            jsonObjectSource.put("signature", signature);

            String jsonString = docObjectMapper.writeValueAsString(jsonObjectSource);

            HttpRequest.newBuilder(new URI(apiCreateDocUrl + "?lp=" + pg));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(apiCreateDocUrl + "?lp=" + pg))
                    .header("Content-Type", "text/plain;charset=UTF-8")
                    .header("Authorization", "Bearer " + token)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonString))
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
//            System.out.println(response);                                     // Demo print
            return response.body();
        } else {
            return "";                                                          // Запрос не выполнен
        }
    }

    private static long millisEstimate(TimeUnit timeUnit) {
        long millisEstimated = 0L;
        switch (timeUnit) {
            case SECONDS:
                millisEstimated = 1000L;
                break;
            case MILLISECONDS:
                millisEstimated = 1L;
                break;
            case MINUTES:
                millisEstimated = 1000 * 60L;
                break;
            case HOURS:
                millisEstimated = 1000 * 60 * 60L;
                break;
            case DAYS:
                millisEstimated = 1000 * 60 * 60 * 24L;
                break;
            case MICROSECONDS:
            case NANOSECONDS:
        }
        return millisEstimated;
    }

    private static int nanoEstimate(TimeUnit timeUnit) {
        int nanoEstimated = 0;
        switch (timeUnit) {
            case SECONDS:
            case MILLISECONDS:
            case MINUTES:
            case HOURS:
            case DAYS:
                break;
            case MICROSECONDS:
                nanoEstimated = 1000;
                break;
            case NANOSECONDS:
                nanoEstimated = 1;
        }
        return nanoEstimated;
    }

    static class CrptApiCreateDocInvoke implements Runnable {

        Semaphore sem;
        static String stringResult;

        public CrptApiCreateDocInvoke(Semaphore sem) throws InterruptedException {
            this.sem = sem;
            System.out.println();                                                   // Demo print
            System.out.println("Вызов CrptApi...");                                 // Demo print
        }

        public static String getStringResult() {
            return stringResult;
        }

        @Override
        public void run() {
            try {
                String signature = "12345";                                             // Demo data

                List<Product> productList = new ArrayList<>();
                Product product1 = new Product("12345", "2025-01-01"
                        , "12345", "01010101010101010101"
                        , "10101010101010101010", "2024-01-01", "0101010101"
                        , "0101010101", "0101010101");

                Product product2 = new Product("67890", "2025-02-01"
                        , "67890", "02020202020202020202"
                        , "20202020202020202020", "2024-02-01", "0101010101"
                        , "0202020202", "0202020202");
                productList.add(product1);
                productList.add(product2);

                Document document = new Document("0123456789", "11111111111111111111"
                        , "123", "Статус", "Тип", false
                        , "22222222222222222222"
                        , "33333333333333333333", "44444444444444444444"
                        , "2025-03-01", Production.OWN_PRODUCTION.toString(), productList
                        , "2025-03-30", "123456");

                try {
                    stringResult = CrptApi.createDoc(document, signature, sem);
                    System.out.println("Получен результат " + stringResult);                // Demo print
                } catch (IOException | URISyntaxException | InterruptedException e) {
                    throw new URISyntaxException("Document creating exception", e.toString());
                }
            } catch (Exception e) {
                System.out.println("Поток прерван " + e);
            }
            System.out.println("Вызов завершен");
        }
    }

    static class RequestTestThread implements Runnable {

        Semaphore sem;
        static int requests = 0;                                       // Счетчик общего количества запросов
        static int requestsSuccessful = 0;                             // Счетчик количества успешных запросов

        public RequestTestThread(Semaphore sem) {
            this.sem = sem;
        }

        public static int getRequests() {
            return requests;
        }

        public static int getRequestsSuccessful() {
            return requestsSuccessful;
        }

        @Override
        public void run() {
            try {
                new CrptApi(sem);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            System.out.println(LocalDateTime.now());
            if (!Objects.equals(CrptApiCreateDocInvoke.getStringResult(), "")) {
                requestsSuccessful++;
            }
            requests++;
        }
    }

    public static void main(String[] args) throws InterruptedException {
//                      Класс для работы с API Честного знака
//                      Данные ограничения количества запросов (demo: пример в секундах)
        TimeUnit timeUnit = SECONDS;
        int requestLimit = 2;                                   // Допустимое количество запросов (с)
//                      Данные периода тестирования
//        int interval = 10;                                    // Интервал времени тестирования (с)
        TimeUnit timeUnitTest = MILLISECONDS;
        int intervalTest = 2000;                                 // Интервал времени между запросами (мс)
        Semaphore sem = new Semaphore(0);
        SemaphoreServiceDemon semaphoreServiceDemon = new SemaphoreServiceDemon(requestLimit, timeUnit, sem);
        Thread semaphoreServiceDemonThread = new Thread(semaphoreServiceDemon);
        semaphoreServiceDemonThread.setDaemon(true);
        semaphoreServiceDemonThread.start();
//                      Поток запросов для вызова метода
//                      "Создание документа для ввода в оборот товара, произведенного в РФ"
//                      (demo: пример)
        System.out.println("Demo-запуск запросов с ограничением количества запросов в секунду");
        System.out.println();
        System.out.println("Время старта потока запросов:");
        LocalDateTime localDateTimeStart = LocalDateTime.now();
        System.out.println(localDateTimeStart);

        ScheduledExecutorService service = Executors.newScheduledThreadPool(10);
        service.schedule(new RequestTestThread(sem), intervalTest, timeUnitTest);
        Thread.sleep(intervalTest);
        service.shutdown();

        LocalDateTime localDateTimeEnd = LocalDateTime.now();
        Thread.sleep(1200);
        System.out.println();
        System.out.println("Время окончания потока запросов:");
        System.out.println(localDateTimeEnd);
        System.out.println();
        System.out.println("Итого интервал времени в секундах:");
        long seconds = localDateTimeEnd.getSecond() - localDateTimeStart.getSecond();
        System.out.println(seconds);
        System.out.println("Максимально допустимое количество запросов в секунду:");
        System.out.println(requestLimit);
        System.out.println("Фактическое количество запросов за интервал времени:");
        System.out.println(RequestTestThread.getRequests());
        System.out.println("Фактическое количество успешных запросов за интервал времени:");
        System.out.println(RequestTestThread.getRequestsSuccessful());
        System.out.println("Фактическое среднее количество успешных запросов в секунду:");
        double taskPerPeriod = (double) RequestTestThread.getRequestsSuccessful() / seconds;
        System.out.println(taskPerPeriod);
    }

    static class Document {
        private String description;
        private String participantInn;
        private String doc_id;
        private String doc_status;
        private String doc_type;
        private boolean importRequest;
        private String owner_inn;
        private String participant_inn;
        private String producer_inn;
        private String production_date;
        private String production_type;
        private List<Product> product;
        private String reg_date;
        private String reg_number;

        public Document(String description, String participantInn, String doc_id, String doc_status
                , String doc_type, boolean importRequest, String owner_inn, String participant_inn
                , String producer_inn, String production_date, String production_type, List<Product> product
                , String reg_date, String reg_number) {
            this.description = description;
            this.participantInn = participantInn;
            this.doc_id = doc_id;
            this.doc_status = doc_status;
            this.doc_type = doc_type;
            this.importRequest = importRequest;
            this.owner_inn = owner_inn;
            this.participant_inn = participant_inn;
            this.producer_inn = producer_inn;
            this.production_date = production_date;
            this.production_type = production_type;
            this.product = product;
            this.reg_date = reg_date;
            this.reg_number = reg_number;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getParticipantInn() {
            return participantInn;
        }

        public void setParticipantInn(String participantInn) {
            this.participantInn = participantInn;
        }

        public String getDoc_id() {
            return doc_id;
        }

        public void setDoc_id(String doc_id) {
            this.doc_id = doc_id;
        }

        public String getDoc_status() {
            return doc_status;
        }

        public void setDoc_status(String doc_status) {
            this.doc_status = doc_status;
        }

        public String getDoc_type() {
            return doc_type;
        }

        public void setDoc_type(String doc_type) {
            this.doc_type = doc_type;
        }

        public boolean isImportRequest() {
            return importRequest;
        }

        public void setImportRequest(boolean importRequest) {
            this.importRequest = importRequest;
        }

        public String getOwner_inn() {
            return owner_inn;
        }

        public void setOwner_inn(String owner_inn) {
            this.owner_inn = owner_inn;
        }

        public String getParticipant_inn() {
            return participant_inn;
        }

        public void setParticipant_inn(String participant_inn) {
            this.participant_inn = participant_inn;
        }

        public String getProducer_inn() {
            return producer_inn;
        }

        public void setProducer_inn(String producer_inn) {
            this.producer_inn = producer_inn;
        }

        public String getProduction_date() {
            return production_date;
        }

        public void setProduction_date(String production_date) {
            this.production_date = production_date;
        }

        public String getProduction_type() {
            return production_type;
        }

        public void setProduction_type(String production_type) {
            this.production_type = production_type;
        }

        public List<Product> getProduct() {
            return product;
        }

        public void setProduct(List<Product> product) {
            this.product = product;
        }

        public String getReg_date() {
            return reg_date;
        }

        public void setReg_date(String reg_date) {
            this.reg_date = reg_date;
        }

        public String getReg_number() {
            return reg_number;
        }

        public void setReg_number(String reg_number) {
            this.reg_number = reg_number;
        }
    }

    static class Product {
        private String certificate_document;
        private String certificate_document_date;
        private String certificate_document_number;
        private String owner_inn;
        private String producer_inn;
        private String production_date;
        private String tnved_code;
        private String uit_code;
        private String uitu_code;

        public Product(String certificate_document, String certificate_document_date
                , String certificate_document_number, String owner_inn, String producer_inn, String production_date
                , String tnved_code, String uit_code, String uitu_code) {
            this.certificate_document = certificate_document;
            this.certificate_document_date = certificate_document_date;
            this.certificate_document_number = certificate_document_number;
            this.owner_inn = owner_inn;
            this.producer_inn = producer_inn;
            this.production_date = production_date;
            this.tnved_code = tnved_code;
            this.uit_code = uit_code;
            this.uitu_code = uitu_code;
        }

        public String getCertificate_document() {
            return certificate_document;
        }

        public void setCertificate_document(String certificate_document) {
            this.certificate_document = certificate_document;
        }

        public String getCertificate_document_date() {
            return certificate_document_date;
        }

        public void setCertificate_document_date(String certificate_document_date) {
            this.certificate_document_date = certificate_document_date;
        }

        public String getCertificate_document_number() {
            return certificate_document_number;
        }

        public void setCertificate_document_number(String certificate_document_number) {
            this.certificate_document_number = certificate_document_number;
        }

        public String getOwner_inn() {
            return owner_inn;
        }

        public void setOwner_inn(String owner_inn) {
            this.owner_inn = owner_inn;
        }

        public String getProducer_inn() {
            return producer_inn;
        }

        public void setProducer_inn(String producer_inn) {
            this.producer_inn = producer_inn;
        }

        public String getProduction_date() {
            return production_date;
        }

        public void setProduction_date(String production_date) {
            this.production_date = production_date;
        }

        public String getTnved_code() {
            return tnved_code;
        }

        public void setTnved_code(String tnved_code) {
            this.tnved_code = tnved_code;
        }

        public String getUit_code() {
            return uit_code;
        }

        public void setUit_code(String uit_code) {
            this.uit_code = uit_code;
        }

        public String getUitu_code() {
            return uitu_code;
        }

        public void setUitu_code(String uitu_code) {
            this.uitu_code = uitu_code;
        }
    }
}
