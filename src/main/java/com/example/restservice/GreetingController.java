package com.example.restservice;

import com.tersesystems.echopraxia.*;
import com.tersesystems.echopraxia.api.Condition;
import com.tersesystems.echopraxia.async.AsyncLogger;
import com.tersesystems.echopraxia.async.AsyncLoggerFactory;
import org.jetbrains.annotations.NotNull;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.concurrent.atomic.AtomicLong;

@RestController
public class GreetingController {

  private static final String template = "Hello, %s!";
  private final AtomicLong counter = new AtomicLong();

  private final Logger<HttpRequestFieldBuilder> logger =
      LoggerFactory.getLogger(getClass(), HttpRequestFieldBuilder.instance)
          .withFields(
              fb ->
                // Any fields that you set in context you can set conditions on later,
                // i.e. on the URI path, content type, or extra headers.
                // These fields will be visible in the JSON file, not shown in console.
                 fb.requestFields(httpServletRequest())
              );

  @NotNull
  private HttpServletRequest httpServletRequest() {
    return ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
  }

  // For an async logger, we need to set thread local context if we have fields that depend on it
  private final AsyncLogger<?> asyncLogger =
      AsyncLoggerFactory.getLogger(HttpRequestFieldBuilder.instance)
          .withThreadContext()
          .withThreadLocal(
              () -> {
                // get the request attributes in rendering thread...
                final RequestAttributes requestAttributes =
                    RequestContextHolder.currentRequestAttributes();
                // ...and the "set" in the runnable will be called in the logging executor's thread
                return () -> RequestContextHolder.setRequestAttributes(requestAttributes);
              })
          .withFields(fb -> fb.requestFields(httpServletRequest()));

  @GetMapping("/greeting")
  public Greeting greeting(@RequestParam(value = "name", defaultValue = "World") String name) {
    // Log using a field builder to add a greeting_name field to JSON
    logger.info("Greetings {}", fb -> fb.string("greeting_name", name));

    // Use a different thread for logging
    asyncLogger.info("this message is logged in a different thread");

    // You can put MDC in
    MDC.put("contextKey", "contextValue");

    // and have it available as fields when you use `withThreadContext()`
    Condition c = (l, ctx) -> ctx.findString("$.?(@.contextKey=contextValue)").isPresent();
    asyncLogger.info(c, "Async loggers are MDC aware");

    // for async logger, if blocks don't work very well, instead use a handle method
    asyncLogger.info(
        h -> {
          // execution in this block takes place in the executor's thread
          h.log("Complex logging statement goes here");
        });

    return new Greeting(counter.incrementAndGet(), String.format(template, name));
  }
}
