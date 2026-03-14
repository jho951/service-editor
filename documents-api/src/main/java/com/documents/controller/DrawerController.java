package com.documents.controller;

import com.documents.domain.Drawer;
import com.documents.dto.DrawerCreateRequest;
import com.documents.dto.DrawerResponse;
import com.documents.dto.DrawerUpdateRequest;
import com.documents.service.DrawerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Drawing", description = "그림판 벡터 JSON 저장/조회/수정/삭제")
@RestController
@RequestMapping("/api/drawings")
@RequiredArgsConstructor
public class DrawerController {

    private final DrawerService service;

    @Operation(summary = "그림 생성")
    @PostMapping
    public ResponseEntity<DrawerResponse> create(@Valid @RequestBody DrawerCreateRequest req) {
        Drawer drawer = Drawer.builder()
            .title(req.getTitle())
            .width(req.getWidth())
            .height(req.getHeight())
            .vectorJson(req.getVectorJson())
            .build();

        UUID id = service.create(drawer);
        DrawerResponse body = DrawerResponse.builder()
            .id(id)
            .title(drawer.getTitle())
            .width(drawer.getWidth())
            .height(drawer.getHeight())
            .vectorJson(drawer.getVectorJson())
            .build();

        return ResponseEntity.created(URI.create("/api/drawings/" + id)).body(body);
    }

    @Operation(summary = "단건 조회")
    @GetMapping("/{id}")
    public ResponseEntity<DrawerResponse> get(@PathVariable UUID id) {
        Drawer row = service.get(id);
        return row == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(toResponse(row));
    }

    @Operation(summary = "부분 수정(PATCH)")
    @PatchMapping("/{id}")
    public ResponseEntity<DrawerResponse> patch(
        @PathVariable UUID id,
        @RequestBody DrawerUpdateRequest req
    ) {
        if (service.get(id) == null) {
            return ResponseEntity.notFound().build();
        }

        Drawer patch = Drawer.builder()
            .title(req.getTitle())
            .width(req.getWidth())
            .height(req.getHeight())
            .vectorJson(req.getVectorJson())
            .build();

        service.updatePartial(id, patch);
        return ResponseEntity.ok(toResponse(service.get(id)));
    }

    @Operation(summary = "삭제")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        if (service.get(id) == null) {
            return ResponseEntity.notFound().build();
        }

        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "목록 조회(페이징)")
    @GetMapping
    public ResponseEntity<List<DrawerResponse>> list(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        List<Drawer> rows = service.list(page, size);
        return ResponseEntity.ok(rows.stream().map(this::toResponse).toList());
    }

    private DrawerResponse toResponse(Drawer drawer) {
        return DrawerResponse.builder()
            .id(drawer.getId())
            .title(drawer.getTitle())
            .width(drawer.getWidth())
            .height(drawer.getHeight())
            .vectorJson(drawer.getVectorJson())
            .createdAt(drawer.getCreatedAt())
            .updatedAt(drawer.getUpdatedAt())
            .build();
    }
}
