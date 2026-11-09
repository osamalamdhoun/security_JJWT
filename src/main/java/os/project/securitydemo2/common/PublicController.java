package os.project.securitydemo2.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class PublicController {
    @GetMapping("/api/public/hello")
    public Map<String, String> hello() {
        return Map.of("message", "Hello! This endpoint does not need a token.");
    }
}

