package lk.ngbcl.player.category;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import lk.ngbcl.player.category.CategoryService.CategoryResponse;
import lk.ngbcl.player.category.CategoryService.PublicCategory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CategoryController {

    private final CategoryService categories;

    public CategoryController(CategoryService categories) {
        this.categories = categories;
    }

    public record CreateCategoryRequest(@NotBlank @Size(max = 60) String name, @Size(max = 300) String description) {
    }

    public record UpdateCategoryRequest(@Size(min = 1, max = 60) String name, @Size(max = 300) String description,
            Boolean active, Long version) {
    }

    public record ReorderRequest(@NotEmpty List<UUID> ids) {
    }

    @GetMapping("/api/public/categories")
    public ResponseEntity<List<PublicCategory>> publicList() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(java.time.Duration.ofSeconds(60)).cachePublic())
                .body(categories.listActivePublic());
    }

    @GetMapping("/api/admin/categories")
    public List<CategoryResponse> list() {
        return categories.list();
    }

    @PostMapping("/api/admin/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(@Valid @RequestBody CreateCategoryRequest body) {
        return categories.create(body.name(), body.description());
    }

    @PatchMapping("/api/admin/categories/{id}")
    public CategoryResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateCategoryRequest body) {
        return categories.update(id, body.name(), body.description(), body.active(), body.version());
    }

    @PutMapping("/api/admin/categories/order")
    public List<CategoryResponse> reorder(@Valid @RequestBody ReorderRequest body) {
        return categories.reorder(body.ids());
    }
}
