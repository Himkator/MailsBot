package com.example.mailsbots.model;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatmemberRepository extends JpaRepository<Chat, Long> {
}
