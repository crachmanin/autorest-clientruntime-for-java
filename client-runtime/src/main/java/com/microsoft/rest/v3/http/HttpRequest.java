/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.rest.v3.http;

import com.microsoft.rest.v3.protocol.HttpResponseDecoder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import reactor.core.publisher.Flux;

import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * The outgoing Http request.
 */
public class HttpRequest {
    private HttpMethod httpMethod;
    private URL url;
    private HttpHeaders headers;
    private Flux<ByteBuf> body;
    private final HttpResponseDecoder responseDecoder;

    /**
     * Create a new HttpRequest instance.
     *
     * @param httpMethod the HTTP request method
     * @param url the target address to send the request to
     * @param responseDecoder decoder to decode response of this request
     */
    public HttpRequest(HttpMethod httpMethod, URL url, HttpResponseDecoder responseDecoder) {
        this.httpMethod = httpMethod;
        this.url = url;
        this.headers = new HttpHeaders();
        this.responseDecoder = responseDecoder;
    }

    /**
     * Create a new HttpRequest instance.
     *
     * @param httpMethod the HTTP request method
     * @param url the target address to send the request to
     * @param headers the HTTP headers to use with this request
     * @param body the request content
     * @param responseDecoder decoder to decode response of this request
     */
    public HttpRequest(HttpMethod httpMethod, URL url, HttpHeaders headers, Flux<ByteBuf> body, HttpResponseDecoder responseDecoder) {
        this.httpMethod = httpMethod;
        this.url = url;
        this.headers = headers;
        this.body = body;
        this.responseDecoder = responseDecoder;
    }

    /**
     * @return the HTTP request method
     */
    public HttpMethod httpMethod() {
        return httpMethod;
    }

    /**
     * Set the HTTP request method.
     *
     * @param httpMethod the HTTP method
     * @return this HttpRequest
     */
    public HttpRequest withHttpMethod(HttpMethod httpMethod) {
        this.httpMethod = httpMethod;
        return this;
    }

    /**
     * @return the target address
     */
    public URL url() {
        return url;
    }

    /**
     * Set the target address to send the request to.
     *
     * @param url target address as {@link URL}
     * @return this HttpRequest
     */
    public HttpRequest withUrl(URL url) {
        this.url = url;
        return this;
    }

    /**
     * @return headers to be sent.
     */
    public HttpHeaders headers() {
        return headers;
    }

    /**
     * Set the request headers.
     *
     * @param headers the set of headers
     * @return this HttpRequest
     */
    public HttpRequest withHeaders(HttpHeaders headers) {
        this.headers = headers;
        return this;
    }

    /**
     * Set a request header, replacing any existing value.
     * A null for {@code value} will remove the header if one with matching name exists.
     *
     * @param name the header name
     * @param value the header value
     * @return this HttpRequest
     */
    public HttpRequest withHeader(String name, String value) {
        headers.set(name, value);
        return this;
    }

    /**
     * @return the content to be send.
     */
    public Flux<ByteBuf> body() {
        return body;
    }

    /**
     * Set the request content.
     *
     * @param content the request content
     * @return this HttpRequest
     */
    public HttpRequest withBody(String content) {
        final byte[] bodyBytes = content.getBytes(StandardCharsets.UTF_8);
        return withBody(bodyBytes);
    }

    /**
     * Set the request content.
     * The Content-Length header will be set based on the given content's length
     *
     * @param content the request content
     * @return this HttpRequest
     */
    public HttpRequest withBody(byte[] content) {
        headers.set("Content-Length", String.valueOf(content.length));
        // Unpooled.wrappedBuffer(body) ByteBuf in unpooled heap
        return withBody(Flux.just(Unpooled.wrappedBuffer(content)));
    }

    /**
     * Set request content.
     *
     * Caller must set the Content-Length header to indicate the length of the content,
     * or use Transfer-Encoding: chunked.
     *
     * @param content the request content
     * @return this HttpRequest
     */
    public HttpRequest withBody(Flux<ByteBuf> content) {
        this.body = content;
        return this;
    }

    /**
     * @return the {@link HttpResponseDecoder} to decodes the response of this request.
     */
    public HttpResponseDecoder responseDecoder() {
        return responseDecoder;
    }

    /**
     * Creates a clone of the request.
     *
     * The main purpose of this is so that this HttpRequest can be changed and the resulting
     * HttpRequest can be a backup. This means that the buffered HttpHeaders and body must
     * not be able to change from side effects of this HttpRequest.
     *
     * @return a new HTTP request instance with cloned instances of all mutable properties.
     */
    public HttpRequest buffer() {
        final HttpHeaders bufferedHeaders = new HttpHeaders(headers);
        return new HttpRequest(httpMethod, url, bufferedHeaders, body, responseDecoder);
    }
}