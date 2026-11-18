package lk.ngbcl.content.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommitteeMemberRepository extends JpaRepository<CommitteeMember, UUID> {

    List<CommitteeMember> findByGroupIdInOrderByDisplayOrderAscFullNameAsc(Collection<UUID> groupIds);

    long countByGroupId(UUID groupId);

    @Query("select coalesce(max(m.displayOrder), 0) from CommitteeMember m where m.groupId = :g")
    int maxOrder(@Param("g") UUID groupId);
}
