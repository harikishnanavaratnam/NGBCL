package lk.ngbcl.player.domain;

import java.util.List;
import java.util.UUID;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlayerRepository extends JpaRepository<Player, UUID> {

    List<Player> findByEmail(String email);

    List<Player> findByPhone(String phone);

    boolean existsBySlug(String slug);

    @Query("select p from Player p where lower(p.fullName) = :name and p.dateOfBirth = :dob")
    List<Player> findByNameAndDob(@Param("name") String lowerName, @Param("dob") LocalDate dob);
}
