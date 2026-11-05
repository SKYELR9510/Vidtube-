package com.silva.vidtube.data;

import androidx.annotation.NonNull;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;

/** Cliente HTTP que usa NewPipe Extractor para hablar con YouTube. */
public final class DownloaderImpl extends Downloader {
    public static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:153.0) Gecko/20100101 Firefox/153.0";

    private static DownloaderImpl instance;
    private final OkHttpClient client;

    private DownloaderImpl() {
        this.client = new OkHttpClient.Builder()
                .readTimeout(30, TimeUnit.SECONDS)
                .connectTimeout(20, TimeUnit.SECONDS)
                .build();
    }

    public static synchronized DownloaderImpl init() {
        if (instance == null) {
            instance = new DownloaderImpl();
        }
        return instance;
    }

    public static DownloaderImpl getInstance() {
        return instance;
    }

    @NonNull
    public OkHttpClient getClient() {
        return client;
    }

    @Override
    public Response execute(@NonNull final Request request)
            throws IOException, ReCaptchaException {
        final String httpMethod = request.httpMethod();
        final String url = request.url();
        final Map<String, List<String>> headers = request.headers();
        final byte[] dataToSend = request.dataToSend();

        RequestBody requestBody = null;
        if (dataToSend != null) {
            requestBody = RequestBody.create(dataToSend);
        }

        final okhttp3.Request.Builder requestBuilder = new okhttp3.Request.Builder()
                .method(httpMethod, requestBody)
                .url(url)
                .addHeader("User-Agent", USER_AGENT);

        for (final Map.Entry<String, List<String>> entry : headers.entrySet()) {
            requestBuilder.removeHeader(entry.getKey());
            for (final String value : entry.getValue()) {
                requestBuilder.addHeader(entry.getKey(), value);
            }
        }

        try (okhttp3.Response response = client.newCall(requestBuilder.build()).execute()) {
            if (response.code() == 429) {
                throw new ReCaptchaException("reCaptcha Challenge requested", url);
            }
            String body = null;
            final ResponseBody responseBody = response.body();
            if (responseBody != null) {
                body = responseBody.string();
            }
            final String latestUrl = response.request().url().toString();
            return new Response(
                    response.code(),
                    response.message(),
                    response.headers().toMultimap(),
                    body,
                    latestUrl);
        }
    }
}
