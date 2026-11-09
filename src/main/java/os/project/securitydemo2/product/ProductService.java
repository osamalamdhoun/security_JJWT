package os.project.securitydemo2.product;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import os.project.securitydemo2.common.PageResponse;
import os.project.securitydemo2.common.ResourceNotFoundException;
import os.project.securitydemo2.product.dto.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {
    private final ProductRepository products;

    public PageResponse<ProductResponse> list(int page, int size) {
        return PageResponse.from(products.findAll(PageRequest.of(page, size, Sort.by("id")))
                .map(ProductMapper::toResponse));
    }

    public ProductResponse get(long id) {
        return ProductMapper.toResponse(find(id));
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        Product product = new Product();
        ProductMapper.copyToEntity(request, product);
        return ProductMapper.toResponse(products.save(product));
    }

    @Transactional
    public ProductResponse update(long id, ProductRequest request) {
        Product product = find(id);
        ProductMapper.copyToEntity(request, product);
        return ProductMapper.toResponse(product);
    }

    @Transactional
    public void delete(long id) {
        products.delete(find(id));
    }

    private Product find(long id) {
        return products.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }
}

