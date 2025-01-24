package actions.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import actions.entity.Student;

public interface StudentRepository extends JpaRepository<Student, Long> {

}
