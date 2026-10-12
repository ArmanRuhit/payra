package com.payra;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	KafkaContainer kafkaContainer() {
		return new KafkaContainer(DockerImageName.parse("apache/kafka-native:4.3.1"));
	}

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:18"));
	}

	@Bean
	@ServiceConnection(name = "redis")
	@SuppressWarnings("resource") // container lifecycle owned by TestContainers + Spring, not us
	GenericContainer<?> redisContainer() {
		return new GenericContainer<>(DockerImageName.parse("redis:8")).withExposedPorts(6379);
	}

}
