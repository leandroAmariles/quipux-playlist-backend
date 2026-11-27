package com.quipux.playlist.infrastructure.out.recommendation;

import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;

/**
 * Makes every request reach the gateway at most once. Turning off the retries of the client library is not enough:
 * the HTTP client sends a request again on its own after a 408, a 503 that allows it, a 307, a 308 or a broken
 * connection. It never does so with a body that can only be written once.
 */
public class SingleAttemptInterceptor implements Interceptor {

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        RequestBody body = request.body();
        if (body == null) {
            return chain.proceed(request);
        }
        return chain.proceed(request.newBuilder().method(request.method(), new OneShotBody(body)).build());
    }

    private static final class OneShotBody extends RequestBody {

        private final RequestBody body;

        private OneShotBody(RequestBody body) {
            this.body = body;
        }

        @Override
        public MediaType contentType() {
            return body.contentType();
        }

        @Override
        public long contentLength() throws IOException {
            return body.contentLength();
        }

        @Override
        public void writeTo(BufferedSink sink) throws IOException {
            body.writeTo(sink);
        }

        @Override
        public boolean isOneShot() {
            return true;
        }
    }
}
