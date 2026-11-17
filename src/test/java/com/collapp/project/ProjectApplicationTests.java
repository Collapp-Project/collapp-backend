package com.collapp.project;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Carga del contexto con H2: no depende del `.env` ni de un PostgreSQL local. */
@SpringBootTest
@ActiveProfiles("test")
class ProjectApplicationTests {

	@Test
	void contextLoads() {
	}

}
