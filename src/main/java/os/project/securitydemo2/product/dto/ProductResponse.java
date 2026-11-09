package os.project.securitydemo2.product.dto;

import java.math.BigDecimal;

public record ProductResponse(Long id, String name, String description, BigDecimal price) {}

