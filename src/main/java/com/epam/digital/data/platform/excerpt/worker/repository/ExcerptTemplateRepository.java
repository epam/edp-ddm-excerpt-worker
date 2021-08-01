package com.epam.digital.data.platform.excerpt.worker.repository;

import com.epam.digital.data.platform.excerpt.dao.ExcerptTemplate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;

public interface ExcerptTemplateRepository extends CrudRepository<ExcerptTemplate, UUID> {

  Optional<ExcerptTemplate> findFirstByTemplateName(String templateName);
}
