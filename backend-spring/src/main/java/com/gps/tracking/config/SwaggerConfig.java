package com.gps.tracking.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("GPS Fleet Tracking System API")
                .version("1.0.0")
                .description("""
                    GPS Vehicle Tracking and Fleet Management System API.
                    
                    This API provides endpoints for:
                    - Driver management and performance analytics
                    - Vehicle tracking and management
                    - Real-time telemetry data
                    - Trip management (entry/exit tracking)
                    - Alert management with notification delivery tracking
                    - Report generation (PDF)
                    - AI-powered chatbot for fleet queries
                    - Raw sensor data (ADXL335, GPS NEO-6M, SPS30, RPi Camera, IMP34DT05, IMP23ABSU, AS8863)
                    - Driver safety scoring and ranking
                    
                    **Swagger UI:** http://localhost:8080/swagger-ui/index.html
                    """)
                .contact(new Contact()
                    .name("Fleet Management Team")
                    .email("admin@trackfleet.com"))
                .license(new License()
                    .name("Proprietary")
                    .url("https://trackfleet.com")))
            .servers(List.of(
                new Server().url("http://localhost:8080").description("Local Development"),
                new Server().url("http://localhost:8080").description("WSL2 Development")
            ));
    }
}
