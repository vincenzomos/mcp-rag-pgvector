package com.ragpgvector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MCPRagVectorApplication {

	public static void main(String[] args) {
		SpringApplication.run(MCPRagVectorApplication.class, args);
	}

//    @Bean
//    public ToolCallbackProvider myMcpTools(HoursMcpTools hoursMcpTools) {
//        return MethodToolCallbackProvider.builder()
//                .toolObjects(hoursMcpTools) // De klasse waar je @Tool methodes in staan
//                .build();
//    }
}
