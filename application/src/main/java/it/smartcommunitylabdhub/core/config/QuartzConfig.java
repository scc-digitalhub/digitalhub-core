/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

/*
 * Copyright 2025 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * https://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */

package it.smartcommunitylabdhub.core.config;

import java.util.Properties;
import javax.sql.DataSource;
import org.quartz.Scheduler;
import org.quartz.spi.JobFactory;
import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

@Configuration
@Order(10)
public class QuartzConfig {

    @Value("${spring.sql.init.platform}")
    private String SQL_PLATFORM;

    // Configura il JobFactory per integrare i job con Spring
    @Bean
    public JobFactory jobFactory() {
        return new SpringBeanJobFactory() {
            @Override
            protected Object createJobInstance(TriggerFiredBundle bundle) throws Exception {
                return super.createJobInstance(bundle);
            }
        };
    }

    // Configura lo SchedulerFactoryBean
    @Bean
    public SchedulerFactoryBean schedulerFactoryBean(JobFactory jobFactory, DataSource dataSource) {
        SchedulerFactoryBean factory = new SchedulerFactoryBean();

        // Configura il DataSource per il JDBC JobStore
        factory.setDataSource(dataSource);

        // Configura il JobFactory
        factory.setJobFactory(jobFactory);

        // Configura le proprietà di Quartz
        factory.setQuartzProperties(quartzProperties());

        return factory;
    }

    // Proprietà di Quartz configurate via codice
    private Properties quartzProperties() {
        Properties properties = new Properties();
        properties.setProperty("org.quartz.scheduler.instanceName", "CoreQuartzScheduler");
        properties.setProperty("org.quartz.scheduler.instanceId", "AUTO");
        //properties.setProperty("org.quartz.jobStore.class", "org.quartz.impl.jdbcjobstore.JobStoreTX");
        if (SQL_PLATFORM.equalsIgnoreCase("PostgreSQL")) {
            properties.setProperty(
                "org.quartz.jobStore.driverDelegateClass",
                "org.quartz.impl.jdbcjobstore.PostgreSQLDelegate"
            );
        } else {
            properties.setProperty(
                "org.quartz.jobStore.driverDelegateClass",
                "org.quartz.impl.jdbcjobstore.StdJDBCDelegate"
            );
        }
        properties.setProperty("org.quartz.jobStore.tablePrefix", "QRTZ_");
        return properties;
    }

    // Configura lo Scheduler
    @Bean
    public Scheduler scheduler(SchedulerFactoryBean schedulerFactoryBean) {
        return schedulerFactoryBean.getScheduler();
    }
}
