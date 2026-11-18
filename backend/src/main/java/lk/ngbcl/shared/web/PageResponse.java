package lk.ngbcl.shared.web;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** Stable pagination envelope (matches frontend lib/api/types.ts Page<T>). */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
