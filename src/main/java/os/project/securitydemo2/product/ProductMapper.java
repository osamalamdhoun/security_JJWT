package os.project.securitydemo2.product;

import os.project.securitydemo2.product.dto.ProductRequest;
import os.project.securitydemo2.product.dto.ProductResponse;

public final class ProductMapper {
    private ProductMapper() {}

    public static void copyToEntity(ProductRequest request, Product product) {
        product.setName(request.name().strip());
        product.setDescription(request.description());
        product.setPrice(request.price());
    }

    public static ProductResponse toResponse(Product product) {
        return new ProductResponse(product.getId(), product.getName(),
                product.getDescription(), product.getPrice());
    }
}

