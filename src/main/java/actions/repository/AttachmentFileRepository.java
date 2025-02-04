package actions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import actions.entity.AttachmentFile;

@Repository
public interface AttachmentFileRepository extends JpaRepository<AttachmentFile, Long>{

}
