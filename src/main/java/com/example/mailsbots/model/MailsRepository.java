package com.example.mailsbots.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.CrudRepository;

public interface MailsRepository extends JpaRepository<Mails, Long> {
}
