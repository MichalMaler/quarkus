package io.quarkus.webjar.locator.runtime;

import java.util.Map;

import io.quarkus.runtime.annotations.Recorder;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;

@Recorder
public class WebJarLocatorRecorder {

    public Handler<RoutingContext> getHandler(String webjarsRootUrl, Map<String, String> webjarNameToVersionMap) {
        return (event) -> {
            String path = event.normalizedPath();
            if (path.startsWith(webjarsRootUrl)) {
                String rest = path.substring(webjarsRootUrl.length());
                String webjar = rest.substring(0, rest.indexOf('/'));
                if (webjarNameToVersionMap.containsKey(webjar)) {
                    // Check this is not the actual path (ex: /webjars/jquery/${jquery.version}/...
                    int endOfVersion = rest.indexOf('/', rest.indexOf('/') + 1);
                    if (endOfVersion == -1) {
                        endOfVersion = rest.length();
                    }
                    String nextPathEntry = rest.substring(rest.indexOf('/') + 1, endOfVersion);
                    if (webjarNameToVersionMap.get(webjar) == null
                            || nextPathEntry.equals(webjarNameToVersionMap.get(webjar))) {
                        // go to the next handler (which should be the static resource handler, if one exists)
                        event.next();
                    } else {
                        // reroute to the real resource
                        event.reroute(webjarsRootUrl + webjar + "/"
                                + webjarNameToVersionMap.get(webjar) + rest.substring(rest.indexOf('/')));
                    }
                } else {
                    // this is not a webjar that we know about
                    event.fail(404);
                }
            } else {
                // should not happen if route is set up correctly
                event.next();
            }
        };
    }

    public Handler<RoutingContext> getImportMapHandler(String expectedPath, String importmap) {
        return new Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext event) {
                String path = event.normalizedPath();
                if (path.equals(expectedPath)) {
                    HttpServerResponse response = event.response();
                    response.headers().set(HttpHeaders.CONTENT_TYPE, "text/javascript");
                    response.end(JAVASCRIPT_CODE.formatted(importmap));
                } else {
                    // should not happen if route is set up correctly
                    event.next();
                }
            }
        };
    }

    private static final String JAVASCRIPT_CODE = """
            const im = document.createElement('script');
            im.type = 'importmap';
            im.textContent = JSON.stringify(%s);
            document.currentScript.after(im);
            """;
}
