/*
 * #%L
 * Wildfly Camel :: Example :: Camel ActiveMQ
 * %%
 * Copyright (C) 2013 - 2014 RedHat
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package org.wildfly.camel.examples.activemq;

import javax.annotation.Resource;
import javax.ejb.Startup;
import javax.enterprise.context.ApplicationScoped;
import javax.jms.ConnectionFactory;

import org.apache.activemq.camel.component.ActiveMQComponent;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cdi.ContextName;

@Startup
@ApplicationScoped
@ContextName("amq-cdi-context")
public class ActiveMQRouteBuilder extends RouteBuilder {

    /**
     * Inject the ActiveMQConnectionFactory that has been configured through the ActiveMQ Resource Adapter
     */
    @Resource(mappedName = "java:/ActiveMQConnectionFactory")
    private ConnectionFactory connectionFactory;

    @Override
    public void configure() throws Exception {

        /**
         * Configure the ActiveMQ component
         */
        ActiveMQComponent activeMQComponent = ActiveMQComponent.activeMQComponent();
        activeMQComponent.setConnectionFactory(connectionFactory);
        getContext().addComponent("activemq", activeMQComponent);

        /**
         * This route reads files placed within JBOSS_HOME/standalone/data/orders
         * and places them onto ActiveMQ queue 'ordersQueue'
         */
        from("file://{{jboss.server.data.dir}}/orders")
        .convertBodyTo(String.class)
        // Remove headers to ensure we end up with unique file names being generated in the next route
        .removeHeaders("*")
        .to("activemq:queue:OrdersQueue");

        /**
         * This route consumes messages from the 'ordersQueue'. Then, based on the
         * message payload XML content it uses a content based router to output
         * orders into appropriate country directories
         */
        from("activemq:queue:OrdersQueue")
            .choice()
                .when(xpath("/order/customer/country = 'UK'"))
                    .log("Sending order ${file:name} to the UK")
                    .to("file:{{jboss.server.data.dir}}/orders/processed/UK")
                .when(xpath("/order/customer/country = 'US'"))
                    .log("Sending order ${file:name} to the US")
                    .to("file:{{jboss.server.data.dir}}/orders/processed/US")
                .otherwise()
                    .log("Sending order ${file:name} to another country")
                    .to("file://{{jboss.server.data.dir}}/orders/processed/Others");
    }
}
