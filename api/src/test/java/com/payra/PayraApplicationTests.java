package com.payra;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest 
public class PayraApplicationTests {
    
    @Test
    void contextLoads() {
        
    }
}
