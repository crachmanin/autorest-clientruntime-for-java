/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.v3;

import com.microsoft.rest.v3.RestException;
import com.microsoft.rest.v3.RestProxy;
import com.microsoft.rest.v3.SwaggerMethodParser;
import com.microsoft.rest.v3.http.ContextData;
import com.microsoft.rest.v3.http.HttpRequest;
import com.microsoft.rest.v3.http.HttpResponse;
import com.microsoft.rest.v3.protocol.HttpResponseDecoder;
import com.microsoft.rest.v3.protocol.SerializerEncoding;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Type;
import java.time.Duration;

/**
 * An abstract class for the different strategies that an OperationStatus can use when checking the
 * status of a long running operation.
 */
abstract class PollStrategy {
    private final RestProxy restProxy;
    private final SwaggerMethodParser methodParser;

    private long delayInMilliseconds;
    private String status;

    PollStrategy(PollStrategyData data) {
        this.restProxy = data.restProxy;
        this.methodParser = data.methodParser;
        this.delayInMilliseconds = data.delayInMilliseconds;
    }

    abstract static class PollStrategyData implements Serializable {
        transient RestProxy restProxy;
        transient SwaggerMethodParser methodParser;
        long delayInMilliseconds;

        PollStrategyData(RestProxy restProxy,
                                SwaggerMethodParser methodParser,
                                long delayInMilliseconds) {
            this.restProxy = restProxy;
            this.methodParser = methodParser;
            this.delayInMilliseconds = delayInMilliseconds;
        }


        abstract PollStrategy initializeStrategy(RestProxy restProxy,
                                        SwaggerMethodParser methodParser);
    }

    @SuppressWarnings("unchecked")
    protected <T> T deserialize(String value, Type returnType) throws IOException {
        return (T) restProxy.serializer().deserialize(value, returnType, SerializerEncoding.JSON);
    }

    protected Mono<HttpResponse> ensureExpectedStatus(HttpResponse httpResponse) {
        return ensureExpectedStatus(httpResponse, null);
    }

    protected Mono<HttpResponse> ensureExpectedStatus(HttpResponse httpResponse, int[] additionalAllowedStatusCodes) {
        return restProxy.ensureExpectedStatus(httpResponse, methodParser, additionalAllowedStatusCodes);
    }

    protected String fullyQualifiedMethodName() {
        return methodParser.fullyQualifiedMethodName();
    }

    protected boolean expectsResourceResponse() {
        return methodParser.expectsResponseBody();
    }

    /**
     * Set the delay in milliseconds to 0.
     */
    final void clearDelayInMilliseconds() {
        this.delayInMilliseconds = 0;
    }

    /**
     * Update the delay in milliseconds from the provided HTTP poll response.
     * @param httpPollResponse The HTTP poll response to update the delay in milliseconds from.
     */
    final void updateDelayInMillisecondsFrom(HttpResponse httpPollResponse) {
        final Long parsedDelayInMilliseconds = delayInMillisecondsFrom(httpPollResponse);
        if (parsedDelayInMilliseconds != null) {
            delayInMilliseconds = parsedDelayInMilliseconds;
        }
    }

    static Long delayInMillisecondsFrom(HttpResponse httpResponse) {
        Long result = null;

        final String retryAfterSecondsString = httpResponse.headerValue("Retry-After");
        if (retryAfterSecondsString != null && !retryAfterSecondsString.isEmpty()) {
            result = Long.valueOf(retryAfterSecondsString) * 1000;
        }

        return result;
    }

    /**
     * If this OperationStatus has a retryAfterSeconds value, return an Mono that is delayed by the
     * number of seconds that are in the retryAfterSeconds value. If this OperationStatus doesn't have
     * a retryAfterSeconds value, then return an Single with no delay.
     * @return A Mono with delay if this OperationStatus has a retryAfterSeconds value.
     */
    Mono<Void> delayAsync() {
        Mono<Void> result = Mono.empty();
        if (delayInMilliseconds > 0) {
            result = result.delaySubscription(Duration.ofMillis(delayInMilliseconds));
        }
        return result;
    }

    /**
     * @return the current status of the long running operation.
     */
    String status() {
        return status;
    }

    /**
     * Set the current status of the long running operation.
     * @param status The current status of the long running operation.
     */
    void setStatus(String status) {
        this.status = status;
    }

    protected final HttpResponseDecoder createResponseDecoder() {
        return new HttpResponseDecoder(methodParser, restProxy.serializer());
    }

    /**
     * Create a new HTTP poll request.
     * @return A new HTTP poll request.
     */
    abstract HttpRequest createPollRequest();

    /**
     * Update the status of this PollStrategy from the provided HTTP poll response.
     * @param httpPollResponse The response of the most recent poll request.
     * @return A Completable that can be used to chain off of this operation.
     */
    abstract Mono<HttpResponse> updateFromAsync(HttpResponse httpPollResponse);

    /**
     * Get whether or not this PollStrategy's long running operation is done.
     * @return Whether or not this PollStrategy's long running operation is done.
     */
    abstract boolean isDone();

    Mono<HttpResponse> sendPollRequestWithDelay() {
        return Mono.defer(() -> delayAsync().then(Mono.defer(() -> {
            final HttpRequest pollRequest = createPollRequest();
            return restProxy.sendHttpRequestAsync(pollRequest, ContextData.NONE);
        })).flatMap(response -> updateFromAsync(response)));
    }

    Mono<OperationStatus<Object>> createOperationStatusMono(HttpRequest httpRequest, HttpResponse httpResponse, SwaggerMethodParser methodParser, Type operationStatusResultType) {
        OperationStatus<Object> operationStatus;
        if (!isDone()) {
            operationStatus = new OperationStatus<>(this, httpRequest);
        } else {
            try {
                final Object resultObject = restProxy.handleRestReturnType(httpRequest, Mono.just(httpResponse), methodParser, operationStatusResultType);
                operationStatus = new OperationStatus<>(resultObject, status());
            } catch (RestException e) {
                operationStatus = new OperationStatus<>(e, OperationState.FAILED);
            }
        }
        return Mono.just(operationStatus);
    }

    Flux<OperationStatus<Object>> pollUntilDoneWithStatusUpdates(final HttpRequest originalHttpRequest, final SwaggerMethodParser methodParser, final Type operationStatusResultType) {
            return sendPollRequestWithDelay()
                    .flatMap(httpResponse -> createOperationStatusMono(originalHttpRequest, httpResponse, methodParser, operationStatusResultType))
                    .repeat()
                    .takeUntil(operationStatus -> isDone());
    }

    Mono<HttpResponse> pollUntilDone() {
        return sendPollRequestWithDelay()
                .repeat()
                .takeUntil(ignored -> isDone())
                .last();
    }

    /**
     * @return The data for the strategy.
     */
    public abstract Serializable strategyData();

    SwaggerMethodParser methodParser() {
        return this.methodParser;
    }
}
