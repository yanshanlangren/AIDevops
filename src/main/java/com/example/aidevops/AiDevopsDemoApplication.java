package com.example.aidevops;

import com.example.aidevops.config.AppProperties;
import com.example.aidevops.config.ExecutionProperties;
import com.example.aidevops.config.GithubProperties;
import com.example.aidevops.config.ModelProperties;
import com.example.aidevops.config.OutputProperties;
import com.example.aidevops.config.PolicyProperties;
import com.example.aidevops.config.RepoProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
        AppProperties.class,
        RepoProperties.class,
        ExecutionProperties.class,
        OutputProperties.class,
        GithubProperties.class,
        ModelProperties.class,
        PolicyProperties.class
})
public class AiDevopsDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiDevopsDemoApplication.class, args);
    }
}
