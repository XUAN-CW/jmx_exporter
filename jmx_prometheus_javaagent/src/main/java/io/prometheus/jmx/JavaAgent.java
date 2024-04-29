/*
 * Copyright (C) 2015-2023 The Prometheus jmx_exporter Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.prometheus.jmx;

import io.prometheus.jmx.common.http.ConfigurationException;
import io.prometheus.jmx.common.http.HTTPServerFactory;
import io.prometheus.metrics.config.PrometheusProperties;
import io.prometheus.metrics.core.metrics.GaugeWithCallback;
import io.prometheus.metrics.core.metrics.SummaryWithCallback;
import io.prometheus.metrics.exporter.httpserver.HTTPServer;
import io.prometheus.metrics.instrumentation.jvm.JvmGarbageCollectorMetrics;
import io.prometheus.metrics.instrumentation.jvm.JvmMetrics;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.prometheus.metrics.model.snapshots.Quantiles;
import io.prometheus.metrics.model.snapshots.Unit;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.lang.management.GarbageCollectorMXBean;
import java.net.InetAddress;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JavaAgent {

    public static final String CONFIGURATION_REGEX =
            "^(?:((?:[\\w.-]+)|(?:\\[.+])):)?"
                    + // host name, or ipv4, or ipv6 address in brackets
                    "(\\d{1,5}):"
                    + // port
                    "(.+)"; // config file

    private static final String DEFAULT_HOST = "0.0.0.0";

    private static HTTPServer httpServer;

        public static void main(String[] args) {
        try {
            Config config = parseConfig("127.0.0.1:16533:/Users/xuanchengwei/my-data/core/java/jmx_exporter/jmx_prometheus_httpserver/src/deb/config/jmx_exporter.yaml");

            JvmGarbageCollectorMetrics.builder(PrometheusProperties.get()).register(PrometheusRegistry.defaultRegistry);

            GaugeWithCallback.builder(PrometheusProperties.get())
                    .name("test")
                    .help("Time spent in a given JVM garbage collector in seconds.")
                    .labelNames("thread_pool","status") // Add the "random" label
                    .callback(callback -> {
                        String[] threadPoolArray = new String[]{"default","dubbo"};
                        String[] threadPoolStatusArray = new String[]{"OK","ERROR"};
                        for (String tp : threadPoolArray) {
                            for (String s : threadPoolStatusArray) {
                                // Generate a random value for the "random" label
                                int randomValue = (int) (Math.random() * 100);
                                callback.call(randomValue, tp, s);
                            }
                        }

                    })
                    .register(PrometheusRegistry.defaultRegistry);


            String host = config.host != null ? config.host : DEFAULT_HOST;

            httpServer =
                    new HTTPServerFactory()
                            .createHTTPServer(
                                    InetAddress.getByName(host),
                                    config.port,
                                    PrometheusRegistry.defaultRegistry,
                                    new File(config.file));
        } catch (Throwable t) {
            synchronized (System.err) {
                System.err.println("Failed to start Prometheus JMX Exporter");
                System.err.println();
                t.printStackTrace();
                System.err.println();
                System.err.println("Prometheus JMX Exporter exiting");
                System.err.flush();
            }
            System.exit(1);
        }
    }

    public static void agentmain(String agentArgument, Instrumentation instrumentation)
            throws Exception {
        premain(agentArgument, instrumentation);
    }

    public static void premain(String agentArgument, Instrumentation instrumentation)
            throws Exception {
        try {
            Config config = parseConfig(agentArgument);

            new BuildInfoMetrics().register(PrometheusRegistry.defaultRegistry);
            JvmMetrics.builder().register(PrometheusRegistry.defaultRegistry);
            new JmxCollector(new File(config.file), JmxCollector.Mode.AGENT)
                    .register(PrometheusRegistry.defaultRegistry);

            String host = config.host != null ? config.host : DEFAULT_HOST;

            httpServer =
                    new HTTPServerFactory()
                            .createHTTPServer(
                                    InetAddress.getByName(host),
                                    config.port,
                                    PrometheusRegistry.defaultRegistry,
                                    new File(config.file));
        } catch (Throwable t) {
            synchronized (System.err) {
                System.err.println("Failed to start Prometheus JMX Exporter");
                System.err.println();
                t.printStackTrace();
                System.err.println();
                System.err.println("Prometheus JMX Exporter exiting");
                System.err.flush();
            }
            System.exit(1);
        }
    }

    /**
     * Parse the Java Agent configuration. The arguments are typically specified to the JVM as a
     * javaagent as {@code -javaagent:/path/to/agent.jar=<CONFIG>}. This method parses the {@code
     * <CONFIG>} portion.
     *
     * @param args provided agent args
     * @return configuration to use for our application
     */
    private static Config parseConfig(String args) {
        Pattern pattern = Pattern.compile(CONFIGURATION_REGEX);

        Matcher matcher = pattern.matcher(args);
        if (!matcher.matches()) {
            System.err.println(
                    "Usage: -javaagent:/path/to/JavaAgent.jar=[host:]<port>:<yaml configuration"
                            + " file> ");
            throw new ConfigurationException("Malformed arguments - " + args);
        }

        String givenHost = matcher.group(1);
        String givenPort = matcher.group(2);
        String givenConfigFile = matcher.group(3);

        int port = Integer.parseInt(givenPort);

        return new Config(givenHost, port, givenConfigFile);
    }

    private static class Config {

        String host;
        int port;
        String file;

        Config(String host, int port, String file) {
            this.host = host;
            this.port = port;
            this.file = file;
        }
    }
}
