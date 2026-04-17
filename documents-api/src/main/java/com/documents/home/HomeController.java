package com.documents.home;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Home", description = "헬스체크용 API")
@RestController
@RequiredArgsConstructor
public class HomeController {

	@GetMapping("/health")
	public String checkHealth() {
		return "OK";
	}
}
