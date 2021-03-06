/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.allune.quickfixj.spring.boot.starter.autoconfigure.server;

import io.allune.quickfixj.spring.boot.starter.autoconfigure.QuickFixJBootProperties;
import io.allune.quickfixj.spring.boot.starter.connection.ConnectorManager;
import io.allune.quickfixj.spring.boot.starter.connection.SessionSettingsLocator;
import io.allune.quickfixj.spring.boot.starter.exception.ConfigurationException;
import org.quickfixj.jmx.JmxExporter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.autoconfigure.condition.ResourceCondition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import quickfix.*;

import javax.management.JMException;
import javax.management.ObjectName;

/**
 * {@link EnableAutoConfiguration Auto-configuration} that configures the Fix Server
 * {@link ConnectorManager} from the properties.
 *
 * @author Eduardo Sanchez-Ros
 */
@Configuration
@EnableConfigurationProperties(QuickFixJBootProperties.class)
@ConditionalOnBean(value = QuickFixJServerMarkerConfiguration.Marker.class)
@Conditional(QuickFixJServerAutoConfiguration.ServerConfigAvailableCondition.class)
public class QuickFixJServerAutoConfiguration {

    private static final String SYSTEM_VARIABLE_QUICKFIXJ_SERVER_CONFIG = "quickfixj.server.config";

    private static final String QUICKFIXJ_SERVER_CONFIG = "quickfixj-server.cfg";

    @Bean
    @ConditionalOnMissingBean(name = "serverSessionSettings")
    public SessionSettings serverSessionSettings(QuickFixJBootProperties properties) {
        return SessionSettingsLocator.loadSettings(properties.getServer().getConfig(),
                SYSTEM_VARIABLE_QUICKFIXJ_SERVER_CONFIG, "file:./" + QUICKFIXJ_SERVER_CONFIG,
                "classpath:/" + QUICKFIXJ_SERVER_CONFIG);
    }

    @Bean
    @ConditionalOnMissingBean(name = "serverApplication")
    public Application serverApplication() {
        return new ApplicationAdapter();
    }

    @Bean
    @ConditionalOnMissingBean(name = "serverMessageStoreFactory")
    public MessageStoreFactory serverMessageStoreFactory() {
        return new MemoryStoreFactory();
    }

    @Bean
    @ConditionalOnMissingBean(name = "serverLogFactory")
    public LogFactory serverLogFactory() {
        return new ScreenLogFactory(true, true, true);
    }

    @Bean
    @ConditionalOnMissingBean(name = "serverMessageFactory")
    public MessageFactory serverMessageFactory() {
        return new DefaultMessageFactory();
    }

    @Bean
    @ConditionalOnMissingBean(name = "serverAcceptor")
    @ConditionalOnProperty(prefix = "quickfixj.server.concurrent", name = "enabled", havingValue = "false", matchIfMissing = true)
    public Acceptor serverAcceptor(Application serverApplication, MessageStoreFactory serverMessageStoreFactory,
                                   SessionSettings serverSessionSettings, LogFactory serverLogFactory, MessageFactory serverMessageFactory) {

        try {
            return new SocketAcceptor(serverApplication, serverMessageStoreFactory, serverSessionSettings, serverLogFactory, serverMessageFactory);
        } catch (ConfigError configError) {
            throw new ConfigurationException(configError.getMessage(), configError);
        }
    }

    @Bean
    @ConditionalOnMissingBean(name = "serverAcceptor")
    @ConditionalOnProperty(prefix = "quickfixj.server.concurrent", name = "enabled", havingValue = "true")
    public Acceptor serverThreadedAcceptor(Application serverApplication, MessageStoreFactory serverMessageStoreFactory,
                                   SessionSettings serverSessionSettings, LogFactory serverLogFactory, MessageFactory serverMessageFactory) {

        try {
            return new ThreadedSocketAcceptor(serverApplication, serverMessageStoreFactory, serverSessionSettings, serverLogFactory, serverMessageFactory);
        } catch (ConfigError configError) {
            throw new ConfigurationException(configError.getMessage(), configError);
        }
    }

    @Bean
    public ConnectorManager serverConnectionManager(Acceptor serverAcceptor, QuickFixJBootProperties properties) {
        ConnectorManager connectorManager = new ConnectorManager(serverAcceptor);
        connectorManager.setAutoStartup(properties.getServer().isAutoStartup());
        connectorManager.setPhase(properties.getServer().getPhase());
        return connectorManager;
    }

    @Bean
    @ConditionalOnProperty(prefix = "quickfixj.server", name = "jmx-enabled", havingValue = "true")
    @ConditionalOnClass(JmxExporter.class)
    @ConditionalOnSingleCandidate(Acceptor.class)
    @ConditionalOnMissingBean(name = "serverInitiatorMBean")
    public ObjectName serverInitiatorMBean(Acceptor serverAcceptor) {
        try {
            JmxExporter exporter = new JmxExporter();
            return exporter.register(serverAcceptor);
        } catch (JMException e) {
            throw new ConfigurationException(e.getMessage(), e);
        }
    }

    /**
     * {@link ServerConfigAvailableCondition} that checks if the
     * {@code quickfixj.server.config} configuration key is defined.
     */
    static class ServerConfigAvailableCondition extends ResourceCondition {

        ServerConfigAvailableCondition() {
            super("QuickFixJ Server", SYSTEM_VARIABLE_QUICKFIXJ_SERVER_CONFIG,
                    "file:./quickfixj-server.cfg", "classpath:/quickfixj-server.cfg");
        }
    }
}