package os.project.securitydemo2.config;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.demo")
public class DemoProperties {
    private boolean enabled;
    @NotBlank @Email @Size(max = 254)
    private String userEmail;
    @NotBlank @Size(min = 8, max = 72)
    private String userPassword;
    @NotBlank @Email @Size(max = 254)
    private String managerEmail;
    @NotBlank @Size(min = 8, max = 72)
    private String managerPassword;
    @NotBlank @Email @Size(max = 254)
    private String adminEmail;
    @NotBlank @Size(min = 8, max = 72)
    private String adminPassword;
}

