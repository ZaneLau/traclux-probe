package com.geoprobe.service.engine.v42;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.dto.engine.v42.BucketType;
import com.geoprobe.dto.engine.v42.Node3ProbeInput;
import com.geoprobe.dto.engine.v42.Node3SearchGroundingContext;
import com.geoprobe.dto.engine.v42.ProbeExposurePolicy;
import com.geoprobe.dto.engine.v42.RawResponse;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoubaoAdapterTest {

    @Mock
    private ChatLanguageModel samplingModel;

    @Test
    void shouldSwitchToFallbackModelWhenPrimaryDailyBudgetExceeded() {
        SequenceHttpClient httpClient = new SequenceHttpClient(List.of(
                new SequenceHttpClient.ResponseSpec(200, successRealtimeBody(30)),
                new SequenceHttpClient.ResponseSpec(200, successRealtimeBody(20))
        ));
        DoubaoAdapter adapter = new DoubaoAdapter(
                samplingModel,
                new ObjectMapper(),
                httpClient,
                "unit-test-key",
                "https://unit.test",
                List.of("ep-primary", "ep-fallback"),
                true,
                25,
                true,
                120,
                5,
                300
        );

        adapter.sendProbe(sampleProbe(), ModelAdapter.DispatchControl.withRealtimeSearch(true, "doubao"));
        adapter.sendProbe(sampleProbe(), ModelAdapter.DispatchControl.withRealtimeSearch(true, "doubao"));

        List<String> requestBodies = httpClient.requestBodies();
        assertEquals(2, requestBodies.size());
        assertTrue(requestBodies.get(0).contains("\"model\":\"ep-primary\""));
        assertTrue(requestBodies.get(1).contains("\"model\":\"ep-fallback\""));
    }

    @Test
    void shouldSwitchToFallbackModelWhenPrimaryEndpointReturnsQuotaError() {
        SequenceHttpClient httpClient = new SequenceHttpClient(List.of(
                new SequenceHttpClient.ResponseSpec(429, "{\"error\":{\"message\":\"quota exceeded\"}}"),
                new SequenceHttpClient.ResponseSpec(200, successRealtimeBody(18))
        ));
        DoubaoAdapter adapter = new DoubaoAdapter(
                samplingModel,
                new ObjectMapper(),
                httpClient,
                "unit-test-key",
                "https://unit.test",
                List.of("ep-primary", "ep-fallback"),
                true,
                2_000_000,
                false,
                120,
                5,
                300
        );

        RawResponse raw = adapter.sendProbe(
                sampleProbe(),
                ModelAdapter.DispatchControl.withRealtimeSearch(true, "doubao")
        );

        assertTrue(raw.searchRequested());
        assertTrue(raw.searchSatisfied());
        List<String> requestBodies = httpClient.requestBodies();
        assertEquals(2, requestBodies.size());
        assertTrue(requestBodies.get(0).contains("\"model\":\"ep-primary\""));
        assertTrue(requestBodies.get(1).contains("\"model\":\"ep-fallback\""));
    }

    @Test
    void shouldFailFastWhenAllRealtimeModelsExhaustedAndHardStopEnabled() {
        SequenceHttpClient httpClient = new SequenceHttpClient(List.of(
                new SequenceHttpClient.ResponseSpec(200, successRealtimeBody(20)),
                new SequenceHttpClient.ResponseSpec(200, successRealtimeBody(20))
        ));
        DoubaoAdapter adapter = new DoubaoAdapter(
                samplingModel,
                new ObjectMapper(),
                httpClient,
                "unit-test-key",
                "https://unit.test",
                List.of("ep-primary", "ep-fallback"),
                true,
                10,
                true,
                120,
                5,
                300
        );

        adapter.sendProbe(sampleProbe(), ModelAdapter.DispatchControl.withRealtimeSearch(true, "doubao"));
        adapter.sendProbe(sampleProbe(), ModelAdapter.DispatchControl.withRealtimeSearch(true, "doubao"));
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> adapter.sendProbe(sampleProbe(), ModelAdapter.DispatchControl.withRealtimeSearch(true, "doubao"))
        );
        assertTrue(ex.getMessage().contains("日额度已耗尽"));
    }

    @Test
    void shouldPreferLowerUsageModelWhenAllBudgetsExceededAndHardStopDisabled() {
        SequenceHttpClient httpClient = new SequenceHttpClient(List.of(
                new SequenceHttpClient.ResponseSpec(200, successRealtimeBody(20)),
                new SequenceHttpClient.ResponseSpec(200, successRealtimeBody(15)),
                new SequenceHttpClient.ResponseSpec(200, successRealtimeBody(12))
        ));
        DoubaoAdapter adapter = new DoubaoAdapter(
                samplingModel,
                new ObjectMapper(),
                httpClient,
                "unit-test-key",
                "https://unit.test",
                List.of("ep-primary", "ep-fallback"),
                true,
                10,
                false,
                120,
                5,
                300
        );

        adapter.sendProbe(sampleProbe(), ModelAdapter.DispatchControl.withRealtimeSearch(true, "doubao"));
        adapter.sendProbe(sampleProbe(), ModelAdapter.DispatchControl.withRealtimeSearch(true, "doubao"));
        adapter.sendProbe(sampleProbe(), ModelAdapter.DispatchControl.withRealtimeSearch(true, "doubao"));

        List<String> requestBodies = httpClient.requestBodies();
        assertEquals(3, requestBodies.size());
        assertTrue(requestBodies.get(0).contains("\"model\":\"ep-primary\""));
        assertTrue(requestBodies.get(1).contains("\"model\":\"ep-fallback\""));
        assertTrue(requestBodies.get(2).contains("\"model\":\"ep-fallback\""));
    }

    @Test
    void shouldRouteHighValueHardProbeToStrongerModelFirst() {
        FakeHttpClient httpClient = new FakeHttpClient(200, successRealtimeBody(30));
        DoubaoAdapter adapter = new DoubaoAdapter(
                samplingModel,
                new ObjectMapper(),
                httpClient,
                "unit-test-key",
                "https://unit.test",
                List.of("doubao-seed-1-6-lite-251015", "doubao-seed-2-0-pro-260215"),
                true,
                1_900_000,
                false,
                120,
                5,
                300
        );

        adapter.sendProbe(highValueProbe(), ModelAdapter.DispatchControl.withRealtimeSearch(true, "doubao"));
        String sentBody = readRequestBody(httpClient.lastRequest());
        assertTrue(sentBody.contains("\"model\":\"doubao-seed-2-0-pro-260215\""));
    }

    @Test
    void shouldRouteLowValueEasyProbeToCheaperModelFirst() {
        FakeHttpClient httpClient = new FakeHttpClient(200, successRealtimeBody(20));
        DoubaoAdapter adapter = new DoubaoAdapter(
                samplingModel,
                new ObjectMapper(),
                httpClient,
                "unit-test-key",
                "https://unit.test",
                List.of("doubao-seed-2-0-pro-260215", "doubao-seed-1-6-lite-251015"),
                true,
                1_900_000,
                false,
                120,
                5,
                300
        );

        adapter.sendProbe(sampleProbe(), ModelAdapter.DispatchControl.withRealtimeSearch(true, "doubao"));
        String sentBody = readRequestBody(httpClient.lastRequest());
        assertTrue(sentBody.contains("\"model\":\"doubao-seed-1-6-lite-251015\""));
    }

    @Test
    void shouldRecoverThinkContentCitationsAndTokensFromModelResponse() {
        when(samplingModel.generate(anyList())).thenReturn(Response.from(
                AiMessage.from("<think>先归纳用户意图</think>建议先看对比表，参考 https://alpha.example/a 和 [官方说明](https://beta.example/b)。"),
                new TokenUsage(12, 34, 46),
                FinishReason.STOP
        ));

        DoubaoAdapter adapter = new DoubaoAdapter(samplingModel);
        RawResponse rawResponse = adapter.sendProbe(sampleProbe(), ModelAdapter.DispatchControl.earlyStopDisabled());

        assertTrue(rawResponse.fullText().contains("<think>先归纳用户意图</think>"));
        assertEquals("先归纳用户意图", rawResponse.thinkContent());
        assertEquals(List.of("https://beta.example/b", "https://alpha.example/a"), rawResponse.citations());
        assertEquals(12, rawResponse.promptTokenCount());
        assertEquals(34, rawResponse.completionTokenCount());
        assertEquals(46, rawResponse.totalTokenCount());
        assertEquals(34, rawResponse.responseTokenCount());
        assertTrue(rawResponse.latencyMs() >= 0L);
        assertFalse(rawResponse.earlyStoppedFlag());
    }

    @Test
    void shouldMarkLengthFinishReasonAsEarlyStoppedForChatModelPath() {
        when(samplingModel.generate(anyList())).thenReturn(Response.from(
                AiMessage.from("回答在长度上限处被截断"),
                new TokenUsage(12, 1024, 1036),
                FinishReason.LENGTH
        ));

        DoubaoAdapter adapter = new DoubaoAdapter(samplingModel);
        RawResponse rawResponse = adapter.sendProbe(sampleProbe(), ModelAdapter.DispatchControl.earlyStopDisabled());

        assertTrue(rawResponse.earlyStoppedFlag());
        assertEquals(1024, rawResponse.completionTokenCount());
        assertEquals("回答在长度上限处被截断", rawResponse.fullText());
    }

    @Test
    void shouldCallResponsesApiWithWebSearchAndExtractEvidence() throws Exception {
        String json = """
                {
                  "output": [
                    {
                      "type": "message",
                      "content": [
                        {"type":"output_text","text":"建议先看品牌口碑和参数对比。"}
                      ]
                    },
                    {
                      "type": "web_search_call",
                      "action": {
                        "query": "制冰机 评测 对比"
                      },
                      "results": [
                        {
                          "source_type":"web",
                          "url":"https://example.com/a",
                          "title":"评测A",
                          "snippet":"制冰速度与噪音表现"
                        },
                        {
                          "source_type":"web",
                          "url":"https://example.com/b",
                          "title":"评测B",
                          "summary":"售后与质量稳定性"
                        }
                      ]
                    }
                  ],
                  "usage": {
                    "input_tokens": 11,
                    "output_tokens": 22,
                    "total_tokens": 33
                  }
                }
                """;
        FakeHttpClient httpClient = new FakeHttpClient(200, json);
        DoubaoAdapter adapter = new DoubaoAdapter(
                samplingModel,
                new ObjectMapper(),
                httpClient,
                "unit-test-key",
                "https://unit.test",
                "ep-unit-test"
        );

        RawResponse raw = adapter.sendProbe(
                sampleProbe(),
                ModelAdapter.DispatchControl.withRealtimeSearch(true, "doubao")
        );

        assertTrue(raw.searchRequested());
        assertTrue(raw.searchSatisfied());
        assertFalse(raw.searchFallbackFlag());
        assertEquals(2, raw.searchEvidence().size());
        assertEquals("https://example.com/a", raw.searchEvidence().get(0).url());
        assertEquals("https://example.com/b", raw.searchEvidence().get(1).url());
        assertTrue(raw.citations().contains("https://example.com/a"));
        assertTrue(raw.citations().contains("https://example.com/b"));
        assertEquals(11, raw.promptTokenCount());
        assertEquals(22, raw.completionTokenCount());
        assertEquals(33, raw.totalTokenCount());
        assertEquals(1, raw.searchCallCount());
        assertEquals(List.of("制冰机 评测 对比"), raw.searchQueries());

        HttpRequest sentRequest = httpClient.lastRequest();
        assertEquals(URI.create("https://unit.test/responses"), sentRequest.uri());
        String sentBody = readRequestBody(sentRequest);
        assertTrue(sentBody.contains("\"tools\":[{\"type\":\"web_search\"}]"));
        assertTrue(sentBody.contains("\"stream\":false"));
        assertTrue(sentBody.contains("\"model\":\"ep-unit-test\""));
        assertTrue(sentBody.contains("\"role\":\"system\""));
        assertTrue(sentBody.contains("\"role\":\"user\""));
        assertTrue(sentBody.contains("\"type\":\"input_text\""));
        assertTrue(sentBody.contains("隐藏检索范围：当前问题归属于“智能手机”品类"));
        assertTrue(sentBody.contains("不要把隐藏目标品牌注入搜索词或答案"));
        assertTrue(sentBody.contains("\"text\":\"运动相机里急着替换时想确认选得对不对？\""));
    }

    @Test
    void shouldExtractCitationAnnotationsAsSearchEvidence() throws Exception {
        String json = """
                {
                  "output": [
                    {
                      "type": "web_search_call",
                      "action": { "query": "制冰机 选购" },
                      "status": "completed"
                    },
                    {
                      "type": "message",
                      "role": "assistant",
                      "content": [
                        {
                          "type": "output_text",
                          "text": "建议优先看制冰速度与噪音。",
                          "annotations": [
                            {
                              "type": "url_citation",
                              "url": "https://example.com/citation",
                              "title": "制冰机选购报告",
                              "summary": "对比了常见家用品牌。"
                            }
                          ]
                        }
                      ]
                    }
                  ],
                  "usage": {
                    "input_tokens": 9,
                    "output_tokens": 18,
                    "total_tokens": 27
                  }
                }
                """;
        FakeHttpClient httpClient = new FakeHttpClient(200, json);
        DoubaoAdapter adapter = new DoubaoAdapter(
                samplingModel,
                new ObjectMapper(),
                httpClient,
                "unit-test-key",
                "https://unit.test",
                "ep-unit-test"
        );
        RawResponse raw = adapter.sendProbe(
                sampleProbe(),
                ModelAdapter.DispatchControl.withRealtimeSearch(true, "doubao")
        );

        assertTrue(raw.searchRequested());
        assertTrue(raw.searchSatisfied());
        assertEquals(1, raw.searchEvidence().size());
        assertEquals("url_citation", raw.searchEvidence().get(0).sourceType());
        assertEquals("https://example.com/citation", raw.searchEvidence().get(0).url());
        assertTrue(raw.citations().contains("https://example.com/citation"));
        assertEquals(1, raw.searchCallCount());
        assertEquals(List.of("制冰机 选购"), raw.searchQueries());
    }

    @Test
    void shouldEmbedVisibleBrandIntoRealtimeGroundingOnlyWhenProbeAllowsIt() throws Exception {
        FakeHttpClient httpClient = new FakeHttpClient(200, successRealtimeBody(20));
        DoubaoAdapter adapter = new DoubaoAdapter(
                samplingModel,
                new ObjectMapper(),
                httpClient,
                "unit-test-key",
                "https://unit.test",
                "ep-unit-test"
        );

        adapter.sendProbe(
                new Node3ProbeInput(
                        "probe-visible",
                        BucketType.ADVERSARIAL,
                        Map.of("D1", "verification"),
                        Map.of("D1", "确认一下"),
                        null,
                        ProbeExposurePolicy.BRAND_VISIBLE,
                        "性能体验里听说小米这阵子口碑不太行，真的假的？",
                        "family",
                        "reason",
                        Map.of(),
                        new Node3SearchGroundingContext("智能手机", List.of("性能体验", "续航稳定性"), "小米")
                ),
                ModelAdapter.DispatchControl.withRealtimeSearch(true, "doubao")
        );

        String sentBody = readRequestBody(httpClient.lastRequest());
        assertTrue(sentBody.contains("当前问题允许围绕品牌“小米”展开检索"));
    }

    private Node3ProbeInput sampleProbe() {
        return new Node3ProbeInput(
                "probe-1",
                BucketType.ORTHOGONAL,
                Map.of("D1", "verification"),
                Map.of("D1", "就想确认一下"),
                null,
                ProbeExposurePolicy.BRAND_HIDDEN,
                "运动相机里急着替换时想确认选得对不对？",
                "default_render_family",
                "default_render",
                Map.of(),
                new Node3SearchGroundingContext("智能手机", List.of("性能体验", "影像能力", "续航稳定性"), null)
        );
    }

    private Node3ProbeInput highValueProbe() {
        return new Node3ProbeInput(
                "probe-high-value",
                BucketType.ADVERSARIAL,
                Map.of("D5", "acute_emergency"),
                Map.of("D5", "时间很紧"),
                null,
                ProbeExposurePolicy.BRAND_VISIBLE,
                "这个决策会直接影响采购损失，必须马上给出可靠结论。",
                "default_render_family",
                "default_render",
                Map.of(),
                Node3SearchGroundingContext.empty()
        );
    }

    private String readRequestBody(HttpRequest request) {
        HttpRequest.BodyPublisher publisher = request.bodyPublisher().orElseThrow();
        BodyCollector collector = new BodyCollector();
        publisher.subscribe(collector);
        return collector.body();
    }

    private String successRealtimeBody(int totalTokens) {
        int promptTokens = Math.max(1, totalTokens / 3);
        int outputTokens = Math.max(1, totalTokens - promptTokens);
        return """
                {
                  "output": [
                    {
                      "type": "message",
                      "content": [
                        {"type":"output_text","text":"建议先看品牌口碑和参数对比。"}
                      ]
                    },
                    {
                      "type": "web_search_call",
                      "action": {
                        "query": "制冰机 评测 对比"
                      },
                      "results": [
                        {
                          "source_type":"web",
                          "url":"https://example.com/a",
                          "title":"评测A",
                          "snippet":"制冰速度与噪音表现"
                        }
                      ]
                    }
                  ],
                  "usage": {
                    "input_tokens": %d,
                    "output_tokens": %d,
                    "total_tokens": %d
                  }
                }
                """.formatted(promptTokens, outputTokens, totalTokens);
    }

    private static final class FakeHttpClient extends HttpClient {

        private final int statusCode;
        private final String body;
        private final AtomicReference<HttpRequest> lastRequest = new AtomicReference<>();

        private FakeHttpClient(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        private HttpRequest lastRequest() {
            return lastRequest.get();
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<java.time.Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request,
                                        HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException {
            lastRequest.set(request);
            @SuppressWarnings("unchecked")
            HttpResponse<T> response = (HttpResponse<T>) new FakeStringHttpResponse(request, statusCode, body);
            return response;
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                                                                HttpResponse.BodyHandler<T> responseBodyHandler) {
            try {
                return CompletableFuture.completedFuture(send(request, responseBodyHandler));
            } catch (IOException ex) {
                CompletableFuture<HttpResponse<T>> future = new CompletableFuture<>();
                future.completeExceptionally(ex);
                return future;
            }
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                                                                HttpResponse.BodyHandler<T> responseBodyHandler,
                                                                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return sendAsync(request, responseBodyHandler);
        }
    }

    private static final class SequenceHttpClient extends HttpClient {

        private final Deque<ResponseSpec> responses;
        private final List<String> requestBodies = new ArrayList<>();

        private SequenceHttpClient(List<ResponseSpec> responses) {
            this.responses = new ArrayDeque<>(responses);
        }

        private List<String> requestBodies() {
            return List.copyOf(requestBodies);
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<java.time.Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException {
            requestBodies.add(readRequestBodyFromRequest(request));
            ResponseSpec responseSpec = responses.pollFirst();
            if (responseSpec == null) {
                throw new IOException("No response spec configured");
            }
            @SuppressWarnings("unchecked")
            HttpResponse<T> response = (HttpResponse<T>) new FakeStringHttpResponse(request, responseSpec.statusCode(), responseSpec.body());
            return response;
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            try {
                return CompletableFuture.completedFuture(send(request, responseBodyHandler));
            } catch (IOException ex) {
                CompletableFuture<HttpResponse<T>> future = new CompletableFuture<>();
                future.completeExceptionally(ex);
                return future;
            }
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                                                                HttpResponse.BodyHandler<T> responseBodyHandler,
                                                                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return sendAsync(request, responseBodyHandler);
        }

        private String readRequestBodyFromRequest(HttpRequest request) {
            HttpRequest.BodyPublisher publisher = request.bodyPublisher().orElseThrow();
            BodyCollector collector = new BodyCollector();
            publisher.subscribe(collector);
            return collector.body();
        }

        private record ResponseSpec(int statusCode, String body) {
        }
    }

    private record FakeStringHttpResponse(HttpRequest request, int statusCode, String body)
            implements HttpResponse<String> {

        @Override
        public int statusCode() {
            return statusCode;
        }

        @Override
        public HttpRequest request() {
            return request;
        }

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of("Content-Type", List.of("application/json")), (left, right) -> true);
        }

        @Override
        public String body() {
            return body;
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }

    private static final class BodyCollector implements Flow.Subscriber<ByteBuffer> {

        private final StringBuilder builder = new StringBuilder();

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(ByteBuffer item) {
            ByteBuffer copy = item.asReadOnlyBuffer();
            byte[] bytes = new byte[copy.remaining()];
            copy.get(bytes);
            builder.append(new String(bytes, StandardCharsets.UTF_8));
        }

        @Override
        public void onError(Throwable throwable) {
            throw new IllegalStateException("读取请求体失败", throwable);
        }

        @Override
        public void onComplete() {
        }

        private String body() {
            return builder.toString();
        }
    }
}
