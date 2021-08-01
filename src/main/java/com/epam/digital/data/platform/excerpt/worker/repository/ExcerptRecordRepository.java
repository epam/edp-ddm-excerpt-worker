package com.epam.digital.data.platform.excerpt.worker.repository;

import com.epam.digital.data.platform.excerpt.dao.ExcerptRecord;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;

public interface ExcerptRecordRepository extends CrudRepository<ExcerptRecord, UUID> {

}
